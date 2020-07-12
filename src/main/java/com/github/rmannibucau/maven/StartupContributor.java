package com.github.rmannibucau.maven;

import com.github.rmannibucau.maven.xml.ComponentSetParser;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.Lifecycle;
import org.apache.maven.lifecycle.mapping.LifecycleMapping;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.sisu.plexus.Strategies;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;

// todo: make it multimodules friendly
@Component(role = StartupContributor.class, instantiationStrategy = Strategies.LOAD_ON_START)
public class StartupContributor {
    @Inject
    private MavenSession session;

    @Inject
    private PlexusContainer container;

    @Inject
    private DefaultLifecycles defaultLifecycles;

    @PostConstruct
    public void init() {
        final Path root = session.getRequest().getMultiModuleProjectDirectory().toPath();
        final Path configFolder = root.resolve(".extensions/custom");

        final Path mappings = configFolder.resolve("mappings.xml");
        if (Files.exists(mappings)) {
            try {
                final Collection<ComponentSetParser.Binding> bindings = new ComponentSetParser(new String(Files.readAllBytes(mappings), StandardCharsets.UTF_8)).load();
                bindings.forEach(b -> container.addComponent(b.getInstance(), LifecycleMapping.class, b.getRoleHint() == null ? "default" : b.getRoleHint()));
                // bind all mapping to lifecycles to have a 1-1 matching (avoid to duplicate it since we keep the order and it is easier to handle)
                // field is readonly so let's patch it with reflection
                try {
                    final Field lifecycles = defaultLifecycles.getClass().getDeclaredField("lifecycles");
                    if (!lifecycles.isAccessible()) {
                        lifecycles.setAccessible(true);
                    }
                    // sisu creates an EntryMapAdapter which is not writable so let's make it writable
                    final Map<String, Lifecycle> lifecyclesMap = new HashMap<>((Map<String, Lifecycle>) lifecycles.get(defaultLifecycles));
                    lifecycles.set(defaultLifecycles, lifecyclesMap);

                    bindings.stream()
                            .filter(it -> it.getRoleHint() != null) // todo: should just check the module packaging and not set it globally
                            .flatMap(it -> it.getInstance().getLifecycles().entrySet().stream())
                            .forEach(lifecycle -> lifecyclesMap.put(lifecycle.getKey(), toLifecycle(lifecycle.getKey(), lifecycle.getValue())));
                } catch (final Exception e) {
                    throw new IllegalStateException("Incompatible maven version", e);
                }
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private Lifecycle toLifecycle(final String key, final org.apache.maven.lifecycle.mapping.Lifecycle value) {
        return new Lifecycle(key, new ArrayList<>(value.getLifecyclePhases().keySet()), emptyMap());
    }
}
