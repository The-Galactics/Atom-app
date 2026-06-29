package com.atom.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Guards the production TLS certificate pins (US-10.4). The Network Security
 * Config {@code <pin-set>} is enforced by Android at the TLS layer, which is not
 * unit-testable here (verify enforcement manually with a MitM proxy). This test
 * ensures the production host and its pins are present and not accidentally
 * removed or mistyped — a wrong/absent pin would brick the published app.
 */
class NetworkSecurityConfigPinningTest {

    private static final String NSC = "src/main/res/xml/network_security_config.xml";
    private static final String PROD_HOST = "atom.crudzaso.com";
    // SPKI SHA-256 pins extracted from atom.crudzaso.com:443 — leaf + intermediate + root.
    private static final List<String> EXPECTED_PINS = List.of(
            "2gqbVZ9vB8JQmvRklO7adAB9cRGlUkBi0RugkY1xrSY=",   // leaf  CN=atom.crudzaso.com
            "brzvtCELCIZUo4sD/qPX0ccRtPsd3DY6RfmxpOU9oB4=",   // intermediate  Let's Encrypt YE1
            "sCkq5UWXjg+7mKu9lMhhYF5bGLsy7VI/UNW3tccdR7w=");  // root  ISRG Root YE

    @Test
    void productionHostIsPinnedWithExpectedPins() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new File(NSC));

        assertThat(textsOf(doc, "domain")).contains(PROD_HOST);
        assertThat(textsOf(doc, "pin")).containsAll(EXPECTED_PINS);
    }

    private static List<String> textsOf(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            out.add(nodes.item(i).getTextContent().trim());
        }
        return out;
    }
}
