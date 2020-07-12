package com.github.rmannibucau.maven.xml;

import org.apache.maven.lifecycle.mapping.DefaultLifecycleMapping;
import org.apache.maven.lifecycle.mapping.LifecycleMapping;
import org.apache.maven.lifecycle.mapping.LifecyclePhase;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

public class ComponentSetParser {
    private final String content;

    public ComponentSetParser(final String content) {
        this.content = content;
    }

    public Collection<Binding> load() {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        try {
            final SAXParser saxParser = factory.newSAXParser();
            final ComponentSet componentSet = new ComponentSet();
            try (final InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                saxParser.parse(stream, componentSet);
            }
            return componentSet.mappings;
        } catch (final SAXException | ParserConfigurationException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class ComponentSet extends StackHandler {
        private final Collection<Binding> mappings = new ArrayList<>();

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
            switch (qName) {
                case "component-set":
                    push(new Content()); // avoid loops since get() will returns this in characters()
                    break;
                case "components":
                    push(new Components());
                    break;
                default:
                    super.startElement(uri, localName, qName, attributes); // use the stack of handlers
            }
        }

        private class Components extends DefaultHandler {
            @Override
            public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
                switch (qName) {
                    case "component":
                        push(new Component());
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported tag: '" + qName + "'");
                }
            }
        }

        private class Component extends DefaultHandler {
            private final Binding binding = new Binding();

            @Override
            public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
                switch (qName) {
                    case "role":
                        push(new Content() {
                            @Override
                            public void endElement(final String uri, final String localName, final String qName) {
                                if (!LifecycleMapping.class.getName().equals(getValue())) {
                                    throw new IllegalArgumentException("Only '" + LifecycleMapping.class.getName() + "' is supported as implementation");
                                }
                            }
                        });
                        break;
                    case "role-hint":
                        push(new Content() {
                            @Override
                            public void endElement(final String uri, final String localName, final String qName) {
                                binding.roleHint = getValue();
                            }
                        });
                        break;
                    case "implementation":
                        push(new Content() {
                            @Override
                            public void endElement(final String uri, final String localName, final String qName) {
                                if (!DefaultLifecycleMapping.class.getName().equals(getValue())) {
                                    throw new IllegalArgumentException("Only '" + DefaultLifecycleMapping.class.getName() + "' is supported as implementation");
                                }
                            }
                        });
                        break;
                    case "configuration":
                        push(new Configuration());
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported tag: '" + qName + "'");
                }
            }

            @Override
            public void endElement(final String uri, final String localName, final String qName) {
                mappings.add(binding);
            }

            private class Configuration extends DefaultHandler {
                @Override
                public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
                    switch (qName) {
                        case "lifecycles":
                            push(new Lifecycles());
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported tag: '" + qName + "'");
                    }
                }
            }

            private class Lifecycles extends DefaultHandler {
                @Override
                public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
                    switch (qName) {
                        case "lifecycle":
                            push(new Component.Lifecycle());
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported tag: '" + qName + "'");
                    }
                }
            }

            private class Lifecycle extends DefaultHandler {
                private final org.apache.maven.lifecycle.mapping.Lifecycle lifecycle = new org.apache.maven.lifecycle.mapping.Lifecycle();

                public Lifecycle() {
                    lifecycle.setLifecyclePhases(new LinkedHashMap<>());
                }

                @Override
                public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
                    switch (qName) {
                        case "id":
                            push(new Content() {
                                @Override
                                public void endElement(final String uri, final String localName, final String qName) {
                                    lifecycle.setId(getValue());
                                }
                            });
                            break;
                        case "phases": // todo: parse lifecycles (more powerful)
                            push(new Phases());
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported tag: '" + qName + "'");
                    }
                }

                @Override
                public void endElement(final String uri, final String localName, final String qName) {
                    binding.instance.getLifecycles().put(lifecycle.getId(), lifecycle);
                }

                private class Phases extends DefaultHandler {
                    @Override
                    public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
                        push(new Content() {
                            @Override
                            public void endElement(final String uri, final String localName, final String qName) {
                                final LifecyclePhase phase = new LifecyclePhase();
                                phase.set(getValue());
                                lifecycle.getLifecyclePhases().put(qName, phase);
                            }
                        });
                    }
                }
            }
        }
    }

    public static class Binding {
        private String roleHint;
        private final DefaultLifecycleMapping instance = new DefaultLifecycleMapping();

        public String getRoleHint() {
            return roleHint;
        }

        public DefaultLifecycleMapping getInstance() {
            return instance;
        }
    }
}
