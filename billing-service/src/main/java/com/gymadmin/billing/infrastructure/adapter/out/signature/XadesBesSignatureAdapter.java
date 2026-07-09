package com.gymadmin.billing.infrastructure.adapter.out.signature;

import com.gymadmin.billing.domain.port.out.XmlSignaturePort;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xades4j.algorithms.EnvelopedSignatureTransform;
import xades4j.production.DataObjectReference;
import xades4j.production.SignedDataObjects;
import xades4j.production.XadesBesSigningProfile;
import xades4j.production.XadesSigner;
import xades4j.properties.DataObjectDesc;
import xades4j.providers.impl.DirectKeyingDataProvider;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * XAdES-BES digital signature adapter for SRI Ecuador electronic invoicing.
 * All crypto operations run on the boundedElastic scheduler to avoid blocking the reactive chain.
 */
@Component
public class XadesBesSignatureAdapter implements XmlSignaturePort {

    @Override
    public Mono<String> sign(String xmlContent, byte[] p12Content, String p12Password) {
        return Mono.fromCallable(() -> performSign(xmlContent, p12Content, p12Password))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String performSign(String xmlContent, byte[] p12Content, String p12Password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(p12Content), p12Password.toCharArray());

        String alias = ks.aliases().nextElement();
        PrivateKey privateKey = (PrivateKey) ks.getKey(alias, p12Password.toCharArray());
        X509Certificate signingCert = (X509Certificate) ks.getCertificate(alias);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new InputSource(new StringReader(xmlContent)));

        Element elementToSign = doc.getElementById("comprobante");
        if (elementToSign == null) {
            elementToSign = doc.getDocumentElement();
            elementToSign.setIdAttribute("id", true);
        }

        DirectKeyingDataProvider kdp = new DirectKeyingDataProvider(signingCert, privateKey);
        XadesBesSigningProfile profile = new XadesBesSigningProfile(kdp);
        XadesSigner signer = profile.newSigner();

        DataObjectDesc dod = new DataObjectReference("#comprobante")
                .withTransform(new EnvelopedSignatureTransform());
        SignedDataObjects dataObjects = new SignedDataObjects(dod);
        signer.sign(dataObjects, doc.getDocumentElement());

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
