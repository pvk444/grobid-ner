package org.grobid.trainer.stax;

import org.codehaus.stax2.XMLStreamReader2;
import org.grobid.core.data.Entity;
import org.grobid.core.data.Sense;
import org.grobid.core.lexicon.NERLexicon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This parser extracts the text from the wikipedia text annotated by Idillia
 */
public class IdilliaSemDocStaxHandler implements StaxParserContentHandler {
    private static Logger LOGGER = LoggerFactory.getLogger(IdilliaSemDocStaxHandler.class);

    private StringBuilder temporaryAccumulatorText = new StringBuilder();
    private StringBuilder globalTextAccumulator = new StringBuilder();


    private List<List<String>> textVector = new ArrayList<>();
    private List<String> currentVector = null;

    private boolean insideText = false;
    private boolean insideSenseInfo = false;
    private boolean isNE = false;
    private String currentSenseKey;
    private String currentCoarseSenseKey;
    private Entity currentEntity;
    private Sense currentSense;
    private String currentNEType;
    private String currentNESubType;
    private String currentFragmentText;
    private Map<String, Entity> neInfos = null;
    private boolean isDelimiter = false;
    private int sentenceIndex = 0;
    private int paragraphIndex = 0;
    private String language = "en";
    private String documentName = "unknown_document.xml";

    // Threshold ot filter out semi-automatic entities having a lower NE confidence
    private double confidenceThreshold = 0.0;
    private double currentFineConfidence;


    public IdilliaSemDocStaxHandler(String documentName) {
        this.documentName = documentName;
        neInfos = new HashMap<>();
    }

    public IdilliaSemDocStaxHandler(String language, String documentName) {
        this(documentName);
        this.language = language;

    }

    public String getText() {
        return temporaryAccumulatorText.toString();
    }

    @Override
    public void onStartDocument(XMLStreamReader2 reader) {
    }

    @Override
    public void onEndDocument(XMLStreamReader2 reader) {
        System.out.println(globalTextAccumulator);
    }

    @Override
    public void onStartElement(XMLStreamReader2 reader) {
        final String localName = reader.getName().getLocalPart();

        if ("docs".equals(localName)) {
            globalTextAccumulator.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                    .append("\n")
                    .append("<corpus>")
                    .append("\n")
                    .append("\t<subCorpus>")
                    .append("\n");
            return;
        } else if ("doc".equals(localName)) {
            globalTextAccumulator
                    .append("\t\t<document name=\"" + documentName + "\" threshold=\"" + confidenceThreshold + "\"> ")
                            .append("\n");
        }

        if ("sensesInfo".equals(localName)) {
            insideSenseInfo = true;
            return;
        }

        if (insideSenseInfo) {
            if ("sense".equals(localName)) {
                isNE = reader.getAttributeValue(null, "isne") != null;
                currentSenseKey = reader.getAttributeValue(null, "fsk");
                currentCoarseSenseKey = reader.getAttributeValue(null, "csk");

                if (isNE) {
                    currentEntity = new Entity();
                }
//                } else {
//                    currentSense = new Sense();
//                    currentSense.setFineSense(currentSenseKey);
//                    currentSense.setCoarseSense(currentCoarseSenseKey);
//                }
            }
        } else {
            if ("txt".equals(localName)) {
                insideText = true;
            } else if ("frag".equals(localName)) {
//                System.out.println("new fragment");
            } else if ("lc".equals(localName)) {
//                final String punct = reader.getAttributeValue(null, "lc");
//                if ("punct".equals(punct)) {
                isDelimiter = true;
//                } else {
//                    System.out.println("Found LC with attribute = " + punct);
//                }

            } else if ("cs".equals(localName)) {

            } else if ("fs".equals(localName)) {
                currentSenseKey = reader.getAttributeValue(null, "sk");
                currentFineConfidence = Double.parseDouble(reader.getAttributeValue(null, "pc"));
            } else if ("sent".equals(localName)) {
                globalTextAccumulator.append("\t<sentence xml:id=\"P" + paragraphIndex + "E" + sentenceIndex + "\">");
            } else if ("para".equals(localName)) {
                globalTextAccumulator.append("<p xml:lang=\"" + language + "\" xml:id=\"P" + paragraphIndex + "\">\n");
            }
        }

        temporaryAccumulatorText.setLength(0);

    }

    @Override
    public void onEndElement(XMLStreamReader2 reader) {
        final String localName = reader.getName().getLocalPart();

        if ("sensesInfo".equals(localName)) {
            insideSenseInfo = false;
            return;
        } else if ("doc".equals(localName)) {
            globalTextAccumulator
                    .append("\n\t\t</document>");

        } else if ("docs".equals(localName)) {
            globalTextAccumulator.append("\n")
                    .append("\t</subCorpus>")
                    .append("\n")
                    .append("<corpus>");
        }

        if (insideSenseInfo) {
            if ("neT".equals(localName)) {
                currentNEType = getText();
            } else if ("neST".equals(localName)) {
                currentNESubType = getText();
            } else if ("neInfo".equals(localName)) {
                // Here we are taking only the first NER 

                if (currentEntity != null) {
                    if (currentEntity.getType() == null && currentNEType != null) {
                        // try to convert the entity type
                        NERLexicon.NER_Type nerType = NERLexicon.NER_Type.mapIdilia(currentNEType);
                        currentEntity.setType(nerType);
                    }
                    if (currentNESubType != null) {
                        currentEntity.addSubType(currentNESubType);
                    }
                }
            } else if ("sense".equals(localName)) {
                if (currentEntity != null) {
                    neInfos.put(currentSenseKey, currentEntity);
                    currentEntity = null;
                }
                currentNEType = null;
                currentNESubType = null;
            }
            return;
        } else {
            if ("txt".equals(localName)) {
                insideText = false;
                currentFragmentText = getText();
            } else if ("sent".equals(localName)) {
                //end of sentence
                globalTextAccumulator.append("</sentence>").append("\n");
                sentenceIndex++;
            } else if ("frag".equals(localName)) {
                if (isDelimiter) {
                    globalTextAccumulator.append(currentFragmentText);
                    isDelimiter = false;
                } else {
                    if(currentFineConfidence > confidenceThreshold) {
                        currentEntity = neInfos.get(currentSenseKey);
                        if (currentEntity != null) {
                            currentEntity.setRawName(currentFragmentText);
                            globalTextAccumulator.append(currentEntity.toXml());
                        } else {
                            globalTextAccumulator.append(currentFragmentText);
                        }
                    } else {
                        globalTextAccumulator.append(currentFragmentText);
                    }
                }

                currentFragmentText = null;
            } else if ("para".equals(localName)) {
                globalTextAccumulator.append("</p>\n");
                paragraphIndex++;
            }
        }

    }

    @Override
    public void onCharacter(XMLStreamReader2 reader) {
        final String text = reader.getText();
        temporaryAccumulatorText.append(text);
    }

    @Override
    public void onAttribute(XMLStreamReader2 reader) {

    }

    public List<List<String>> getTextVector() {
        return textVector;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public void setConfidenceThreshold(double confidenceThreshold) {
        this.confidenceThreshold = confidenceThreshold;
    }
}
