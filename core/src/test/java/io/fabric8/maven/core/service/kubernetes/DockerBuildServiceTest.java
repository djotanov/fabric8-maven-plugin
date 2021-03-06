/**
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.core.service.kubernetes;

import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.ImagePullPolicy;
import io.fabric8.maven.docker.service.BuildService;
import io.fabric8.maven.docker.service.ImagePullManager;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.AutoPullMode;
import mockit.Expectations;
import mockit.FullVerifications;
import mockit.Mocked;
import mockit.VerificationsInOrder;
import org.junit.Test;

public class DockerBuildServiceTest {

    @Mocked
    private ServiceHub hub;

    @Mocked
    private BuildService buildService;

    @Test
    public void testSuccessfulBuild() throws Exception {

        final BuildService.BuildContext context = new BuildService.BuildContext.Builder()
                .build();

        final io.fabric8.maven.core.service.BuildService.BuildServiceConfig config = new io.fabric8.maven.core.service.BuildService.BuildServiceConfig.Builder()
                .dockerBuildContext(context)
                .imagePullManager(new ImagePullManager(new TestCacheStore(), ImagePullPolicy.Always.name(), AutoPullMode.ALWAYS.name()))
                .build();

        final String imageName = "image-name";
        final ImageConfiguration image = new ImageConfiguration.Builder()
                .name(imageName)
                .buildConfig(new BuildImageConfiguration.Builder()
                        .from("from")
                        .build()
                ).build();

        DockerBuildService service = new DockerBuildService(hub, config);
        service.build(image);

        new VerificationsInOrder() {{
            buildService.buildImage(image, config.getImagePullManager(), context);
            buildService.tagImage(imageName, image);
        }};
    }

    private class TestCacheStore implements ImagePullManager.CacheStore {

        String cache;

        @Override
        public String get(String key) {
            return cache;
        }

        @Override
        public void put(String key, String value) {
            cache = value;
        }
    }

}
