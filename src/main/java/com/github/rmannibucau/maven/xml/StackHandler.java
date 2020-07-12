package com.github.rmannibucau.maven.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.LinkedList;
import java.util.List;

public class StackHandler extends DefaultHandler {
    private final List<DefaultHandler> handlers = new LinkedList<>();

    protected DefaultHandler get() {
        return handlers.get(0);
    }

    protected DefaultHandler pop() {
        return handlers.remove(0);
    }

    protected void push(final DefaultHandler handler) {
        handlers.add(0, handler);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
        get().startElement(uri, localName, qName, attributes);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        get().endElement(uri, localName, qName);
        pop();
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        get().characters(ch, start, length);
    }

    public static class Content extends DefaultHandler {
        private final StringBuilder characters = new StringBuilder();

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
            characters.setLength(0);
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) {
            characters.append(new String(ch, start, length));
        }

        public String getValue() {
            return characters.toString();
        }
    }
}