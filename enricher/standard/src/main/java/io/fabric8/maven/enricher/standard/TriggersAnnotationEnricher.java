/*
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

package io.fabric8.maven.enricher.standard;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.builder.TypedVisitor;
import io.fabric8.kubernetes.api.builder.Visitable;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.extensions.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.extensions.StatefulSetBuilder;
import io.fabric8.maven.core.util.JSONUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.enricher.api.BaseEnricher;
import io.fabric8.maven.enricher.api.EnricherContext;
import io.fabric8.openshift.api.model.ImageChangeTrigger;
import io.fabric8.openshift.api.model.ImageChangeTriggerBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * This adds a `image.openshift.io/triggers` tag to all kubernetes resources in order to make them run on Openshift when using ImageStreams.
 *
 * @author nicola
 * @since 10/05/18
 */
public class TriggersAnnotationEnricher extends BaseEnricher {

    private static final String TRIGGERS_ANNOTATION = "image.openshift.io/triggers";

    public TriggersAnnotationEnricher(EnricherContext buildContext) {
        super(buildContext, "fmp-triggers-annotation");
    }

    @Override
    public void adapt(KubernetesListBuilder builder) {

        builder.accept(new TypedVisitor<StatefulSetBuilder>() {
            @Override
            public void visit(StatefulSetBuilder o) {
                if (canWriteTriggers(o.build())) {
                    o.editOrNewMetadata()
                            .addToAnnotations(TRIGGERS_ANNOTATION, createAnnotation(o))
                            .endMetadata();
                }
            }
        });

        builder.accept(new TypedVisitor<ReplicaSetBuilder>() {
            @Override
            public void visit(ReplicaSetBuilder o) {
                if (canWriteTriggers(o.build())) {
                    o.editOrNewMetadata()
                            .addToAnnotations(TRIGGERS_ANNOTATION, createAnnotation(o))
                            .endMetadata();
                }
            }
        });

        builder.accept(new TypedVisitor<DaemonSetBuilder>() {
            @Override
            public void visit(DaemonSetBuilder o) {
                if (canWriteTriggers(o.build())) {
                    o.editOrNewMetadata()
                            .addToAnnotations(TRIGGERS_ANNOTATION, createAnnotation(o))
                            .endMetadata();
                }
            }
        });

    }

    protected boolean canWriteTriggers(HasMetadata res) {
        return res.getMetadata() == null ||
                res.getMetadata().getAnnotations() == null ||
                !res.getMetadata().getAnnotations().containsKey(TRIGGERS_ANNOTATION);
    }

    protected String createAnnotation(Visitable<?> builder) {
        final List<ImageChangeTrigger> triggerList = new ArrayList<>();
        builder.accept(new TypedVisitor<ContainerBuilder>() {
            @Override
            public void visit(ContainerBuilder cb) {
                Container container = cb.build();
                String containerName = container.getName();
                String containerImage = container.getImage();
                ImageName image = new ImageName(containerImage);
                if (image.getRegistry() == null && image.getUser() == null) {
                    // Imagestreams used as trigger are in the same namespace
                    String tag = image.getTag() != null ? image.getTag() : "latest";

                    ImageChangeTrigger trigger = new ImageChangeTriggerBuilder()
                            .withNewFrom()
                            .withKind("ImageStreamTag")
                            .withName(image.getSimpleName() + ":" + tag)
                            .endFrom()
                            .build();

                    trigger.setAdditionalProperty("fieldPath", "spec.template.spec.containers[?(@.name==\"" + containerName + "\")].image");
                    triggerList.add(trigger);
                }
            }
        });

        try {
            return JSONUtil.mapper().writeValueAsString(triggerList);
        } catch (JsonProcessingException e) {
            getLog().error("Error while creating ImageStreamTag triggers for Kubernetes resources: %s", e);
            return "[]";
        }
    }

}
