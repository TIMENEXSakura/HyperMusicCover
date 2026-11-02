package com.os4.musiccover.lyrics;

import com.mocharealm.accompanist.lyrics.core.utils.SimpleXmlParser;
import com.mocharealm.accompanist.lyrics.core.utils.XmlAttribute;
import com.mocharealm.accompanist.lyrics.core.utils.XmlElement;
import java.util.ArrayList;
import java.util.List;

/** Small Java bridge to the XML reader used by our pinned lyrics-core parser. */
final class TtmlXml {
    static Node parse(String xml) { return new Node(new SimpleXmlParser().parse(xml)); }

    static final class Node {
        private final XmlElement element;
        Node(XmlElement element) { this.element = element; }
        String name() { return element.getName(); }
        String attr(String name) {
            for (XmlAttribute attr : element.getAttributes()) {
                if (attr.getName().equals(name)) return attr.getValue();
            }
            return null;
        }
        List<Node> children() {
            List<Node> out = new ArrayList<>();
            for (XmlElement child : element.getChildren()) out.add(new Node(child));
            return out;
        }
    }
}
