/*
 * Copyright 2023 Rudy De Busscher (https://www.atbash.be)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package be.atbash.runtime.plugin.maven;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.shade.Shader;
import org.apache.maven.plugins.shade.mojo.ArchiveFilter;
import org.apache.maven.plugins.shade.mojo.ShadeMojo;
import org.apache.maven.plugins.shade.resource.IncludeResourceTransformer;
import org.apache.maven.plugins.shade.resource.ManifestResourceTransformer;
import org.apache.maven.plugins.shade.resource.ResourceTransformer;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Goal which simplifies packaging an application within an uber-jar for Atbash Runtime.
 */
@Mojo(name = "assemble", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class RuntimePackageMojo extends AbstractMojo {
    @Parameter(property = "shade.mainClass")
    private String mainClass;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component(hint = "default", role = org.apache.maven.plugins.shade.Shader.class)
    private Shader shader;

    @Parameter(defaultValue = "${project.build.directory}")
    private File outputDirectory;

    public void execute() throws MojoExecutionException {
        // Create a new instance of the Shade plugin
        ShadeMojo shadeMojo = new ShadeMojo();

        // Set the configuration of the Shade plugin
        setFieldValue(shadeMojo, "project", project);
        setFieldValue(shadeMojo, "shader", shader);
        setFieldValue(shadeMojo, "outputFile", new File(outputDirectory, project.getArtifactId() + ".jar"));
        setFieldValue(shadeMojo, "shadedArtifactAttached", Boolean.FALSE);

        ManifestResourceTransformer transformer = new ManifestResourceTransformer();
        transformer.setMainClass(mainClass);

        ResourceTransformer[] transformers = new ResourceTransformer[15];
        transformers[0] = transformer;

        transformers[1] = new SimpleAppendingTransformer("META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate");

        transformers[2] = new SimpleAppendingTransformer("META-INF/services/jakarta.enterprise.inject.spi.Extension");
        transformers[3] = new SimpleAppendingTransformer("META-INF/services/org.glassfish.jersey.internal.spi.AutoDiscoverable");
        transformers[4] = new SimpleAppendingTransformer("META-INF/services/be.atbash.runtime.core.deployment.data.DeploymentDataRetriever");
        transformers[5] = new SimpleAppendingTransformer("META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate");
        transformers[6] = new SimpleAppendingTransformer("META-INF/services/be.atbash.runtime.core.data.module.Module");
        transformers[7] = new SimpleAppendingTransformer("META-INF/microprofile-config.properties");
        transformers[8] = new SimpleAppendingTransformer("META-INF/services/org.glassfish.jersey.internal.spi.ForcedAutoDiscoverable");
        transformers[9] = new SimpleAppendingTransformer("META-INF/services/org.glassfish.jersey.server.spi.ContainerProvider");
        transformers[10] = new SimpleAppendingTransformer("META-INF/services/org.glassfish.jersey.server.spi.WebServerProvider");
        transformers[11] = new SimpleAppendingTransformer("META-INF/services/org.eclipse.microprofile.config.spi.ConfigSourceProvider");
        transformers[12] = new SimpleAppendingTransformer("META-INF/services/org.glassfish.jersey.internal.inject.InjectionManagerFactory");
        transformers[13] = new SimpleAppendingTransformer("META-INF/services/org.eclipse.jetty.webapp.Configuration");

        String outputDirectory = project.getBuild().getOutputDirectory();

        IncludeResourceTransformer includeTransformer = new IncludeResourceTransformer();
        setFieldValue(includeTransformer, "resource", "META-INF/beans.xml");
        setFieldValue(includeTransformer, "file", new File(outputDirectory + "/META-INF/beans.xml"));
        transformers[14] = includeTransformer;


        setFieldValue(shadeMojo, "transformers", transformers);

        ArchiveFilter excludeSignedContent = new ArchiveFilter();
        setFieldValue(excludeSignedContent, "artifact", "*:*");

        Set<String> excludes = new HashSet<>();
        excludes.add("META-INF/*.SF");
        excludes.add("META-INF/*.DSA");
        excludes.add("META-INF/*.RSA");
        // To handle the manual registration of CDI beans in Extensions (MP Config and JWT)
        excludes.add("META-INF/OriginalJarPackaging");

        // To remove the warning of duplicated files. We don't need them
        excludes.add("META-INF/NOTICE.txt");
        excludes.add("META-INF/NOTICE.markdown");
        excludes.add("META-INF/LICENSE.txt");
        excludes.add("META-INF/LICENSE.TXT");
        excludes.add("META-INF/LICENSE.md");
        excludes.add("META-INF/LICENSE");
        excludes.add("META-INF/NOTICE");
        excludes.add("META-INF/NOTICE.md");
        excludes.add("META-INF/DEPENDENCIES.txt");
        excludes.add("META-INF/MANIFEST.MF");
        excludes.add("META-INF/versions/9/module-info.class");
        excludes.add("module-info.class");  // Yep, no module path usage when Runtime Uber Jar.
        excludes.add("META-INF/beans.xml");  // We have added the project one through the IncludeResourceTransformer
        setFieldValue(excludeSignedContent, "excludes", excludes);

        ArchiveFilter[] filters = new ArchiveFilter[]{excludeSignedContent};
        setFieldValue(shadeMojo, "filters", filters);

        // Call the execute() method of the Shade plugin
        try {
            shadeMojo.execute();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to execute Shade plugin", e);
        }
    }

    private void setFieldValue(Object instance, String fieldName, Object value) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(instance, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
