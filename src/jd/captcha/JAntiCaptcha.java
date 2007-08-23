package jd.captcha;

import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.PixelGrabber;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;

import java.net.URL;

import java.net.URLDecoder;
import java.util.Date;
import java.util.Vector;
import java.util.logging.Logger;

import javax.swing.JLabel;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import jd.JDUtilities;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Diese Klasse stellt alle public Methoden zur captcha erkennung zur Verfügung.
 * Sie verküpft Letter und captcha Klassen. Gleichzeitig dient sie als
 * Parameter-Dump.
 * 
 * @author coalado
 */
public class JAntiCaptcha {
    /**
     * Pfad zum Update Ordner
     */
    private static final String UPDATE_PATH = "http://lagcity.de/~JDownloaderFiles/autoUpdate";

    /**
     * Logger
     */
    private static Logger  logger    = UTILITIES.getLogger();

    /**
     * Name des Authors der entsprechenden methode. Wird aus der jacinfo.xml
     * Datei geladen
     */
    private String         methodAuthor;

    /**
     * Methodenname. Wird aus der jacinfo.xml geladen
     */
    private String         methodName;

    /**
     * Bildtyp. Falls dieser von jpg unterschiedlich ist muss zuerst konvertiert
     * werden
     */

    private String         imageType;

    /**
     * Anzahl der Buchstaben im Captcha. Wird aus der jacinfo.xml gelesen
     */
    private int            letterNum;

    /**
     * Pfad zum SourceBild (Standalone). Wird aus der jacinfo.xml gelesen
     */
    private String         sourceImage;

    /**
     * Pfad zur Resulotfile. dort wird der Captchacode hineingeschrieben.
     * (standalone mode)
     */
    private String         resultFile;

    /**
     * XML Dokument für die MTH File
     */

    private Document       mth;

    /**
     * Vector mit den Buchstaben aus der MTHO File
     */
    private Vector<Letter> letterDB;

    /**
     * Static counter. Falls zu debug zecen mal global ein counter gebraucht
     * wird
     */
    public static int      counter   = 0;

    /**
     * Static counter. Falls zu debug zecen mal global ein counter gebraucht
     * wird
     */
    public static int      counterB  = 0;

    /**
     * ordnername der methode
     */
    private String         methodDir;
    private URL            pathMethod;

    /**
     * fenster die eigentlich nur zur entwicklung sind um Basic GUI Elemente zu
     * haben
     */
    @SuppressWarnings("unused")
    private BasicWindow    frame1;

    @SuppressWarnings("unused")
    private BasicWindow    frame2;

    @SuppressWarnings("unused")
    private BasicWindow    frame3;
    @SuppressWarnings("unused")
    private BasicWindow    frame4;

    /**
     * jas Script Instanz. Sie verarbneitet das JACScript und speichert die
     * Parameter
     */
    public JACScript       jas;


    private int[][]        letterMap = null;

    /**
     * @param methodsPath
     * @param methodName
     * @param methodDir
     */
    public JAntiCaptcha(String methodsPath, String methodName) {
if(methodsPath==null){
    methodsPath= UTILITIES.getFullPath(new String[]{JDUtilities.getJDHomeDirectory().getAbsolutePath(),"jd","captcha","metchods"});
}
        this.methodDir = methodName;
        try {
            this.pathMethod = new File(methodsPath + UTILITIES.FS + methodName).toURI().toURL();
        } catch (MalformedURLException e) {  
            e.printStackTrace();
        }

        try {
            if (isMethodPathValid(this.pathMethod)) {
                getJACInfo();
                // jas = new JACScript(this, pathMethod+"/script.jas",method);
                jas = new JACScript(this, UTILITIES.getResourceURL(pathMethod + "script.jas"), methodDir);
                loadMTHFile();
                logger.fine("letter DB loaded: Buchstaben: " + letterDB.size());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }




    /**
     * Prüft ob der übergebene Methodname verfügbar ist.
     * 
     * @param method
     * @return true/false
     */
    private boolean isMethodPathValid(URL method) {
      
        logger.fine("Methods at " + method);
       
            URL url = UTILITIES.getResourceURL(method);
            if (url == null) {
                logger.severe("Die Methode " + method.toString() + "letters.mth" + " kann nicht gefunden werden. JAC Pfad falsch?");
                return false;
            }     

        return true;

    }

 

    /**
     * Diese methode wird aufgerufen um alle captchas im Ordner
     * methods/Methodname/captchas zu trainieren
     * @param path 
     */
    public void trainAllCaptchas(String path) {

        int successFull = 0;
        int total = 0;
        File[] images = getImages(path);
        if (images == null)
            return;
        int newLetters;
        for (int i = 0; i < images.length; i++) {
            logger.fine(images[i].toString());

            newLetters = trainCaptcha(images[i], getLetterNum());

            logger.fine("Erkannt: " + newLetters + "/" + getLetterNum());
            if (newLetters > 0) {
                successFull += newLetters;
                total += getLetterNum();
                logger.info("Erkennungsrate: " + ((100 * successFull / total)));
            }

        }

    }

    /**
     * MTH File wird geladen und verarbeitet
     */
    private void loadMTHFile() throws IOException {

        URL url = UTILITIES.getResourceURL(pathMethod + "letters.mth");
        if (url == null) {
            logger.severe("MTH FILE NOT AVAILABLE.");
            return;
        } else {
            mth = UTILITIES.parseXmlFile(url.openStream(), false);
        }
        createLetterDBFormMTH();
        // sortLetterDB();

    }

    /**
     * Aus gründen der geschwindigkeit wird die MTH XMl in einen vector
     * umgewandelt
     */
    private void createLetterDBFormMTH() {
        letterDB = new Vector<Letter>();
        long start1 = UTILITIES.getTimer();
        try {

            if (mth == null || mth.getFirstChild() == null)
                return;
            NodeList nl = mth.getFirstChild().getChildNodes();
            Letter tmp;
            for (int i = 0; i < nl.getLength(); i++) {
                // Get child node
                Node childNode = nl.item(i);
                if (childNode.getNodeName().equals("letter")) {
                    NamedNodeMap att = childNode.getAttributes();
   
                    tmp = new Letter();
                    tmp.setOwner(this);
                    if (!tmp.setTextGrid(childNode.getTextContent()))
                        continue;
                    ;
                    String id = UTILITIES.getAttribute(childNode, "id");
                    if (id != null) {
                        tmp.id = Integer.parseInt(id);
                    }
                    tmp.setSourcehash(att.getNamedItem("captchaHash").getNodeValue());
                    tmp.setDecodedValue(att.getNamedItem("value").getNodeValue());
                    tmp.setBadDetections(Integer.parseInt(UTILITIES.getAttribute(childNode, "bad")));
                    tmp.setGoodDetections(Integer.parseInt(UTILITIES.getAttribute(childNode, "good")));
                    letterDB.add(tmp);
                } else if (childNode.getNodeName().equals("map")) {
                    logger.fine("Parse LetterMap");
                    long start2 = UTILITIES.getTimer();
                    String[] map = childNode.getTextContent().split("\\|");
                    letterMap = new int[map.length][map.length];
                    for (int x = 0; x < map.length; x++) {
                        String[] row = map[x].split("\\,");
                        for (int y = 0; y < map.length; y++) {
                            letterMap[x][y] = Integer.parseInt(row[y]);
                        }

                    }
                    logger.fine("LetterMap Parsing time: " + (UTILITIES.getTimer() - start2));
                }
            }
        } catch (Exception e) {
            logger.severe("Fehler mein lesen der MTHO Datei!!. Methode kann nicht funktionieren!");

        }
        logger.fine("Mth Parsing time: " + (UTILITIES.getTimer() - start1));
    }

    /*
     * Die Methode parst die jacinfo.xml
     */
    private void getJACInfo() throws IOException {
     
        URL url = UTILITIES.getResourceURL(pathMethod+"jacinfo.xml");
        if (url == null) {
            logger.severe("" + pathMethod + "/jacinfo.xml is missing");
            return;
        }
        Document doc = UTILITIES.parseXmlFile(url.openStream(), false);

        NodeList nl = doc.getFirstChild().getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            // Get child node
            Node childNode = nl.item(i);

            if (childNode.getNodeName().equals("method")) {

                this.setMethodAuthor(UTILITIES.getAttribute(childNode, "author"));
                this.setMethodName(UTILITIES.getAttribute(childNode, "name"));

            }
            if (childNode.getNodeName().equals("format")) {

                this.setLetterNum(Integer.parseInt(UTILITIES.getAttribute(childNode, "letterNum")));
                this.setImageType(UTILITIES.getAttribute(childNode, "type"));

            }
            if (childNode.getNodeName().equals("source")) {

                this.setSourceImage(UTILITIES.getAttribute(childNode, "file"));

            }
            if (childNode.getNodeName().equals("result")) {

                this.setResultFile(UTILITIES.getAttribute(childNode, "file"));

            }

        }

    }

    /**
     * Diese methode trainiert einen captcha
     * 
     * @param captchafile
     *            File zum Bild
     * @param letterNum
     *            Anzahl der Buchstaben im captcha
     * @return int -1: Übersprungen Sonst: anzahl der richtig erkanten Letter
     */

    private Document createXMLFromLetterDB() {

        Document xml = UTILITIES.parseXmlString("<jDownloader/>", false);
        if (letterMap != null) {
            Element element = xml.createElement("map");
            xml.getFirstChild().appendChild(element);
            element.appendChild(xml.createTextNode(this.getLetterMapString()));
        }

        Letter letter;
        for (int i = 0; i < letterDB.size(); i++) {
            letter = letterDB.elementAt(i);
            Element element = xml.createElement("letter");
            xml.getFirstChild().appendChild(element);
            element.appendChild(xml.createTextNode(letter.getPixelString()));
            element.setAttribute("id", i + "");
            element.setAttribute("value", letter.getDecodedValue());
            element.setAttribute("captchaHash", letter.getSourcehash());
            element.setAttribute("good", letter.getGoodDetections() + "");
            element.setAttribute("bad", letter.getBadDetections() + "");

        }
        return xml;

    }

    /**
     * TestFunktion - Annimierte Gifs verarbeiten
     * 
     * @param path
     */
    public void mergeGif(File path) {
        getJas().setColorType("G");
        GifDecoder d = new GifDecoder();
        d.read(path.getAbsolutePath());
        int n = d.getFrameCount();

        logger.fine("Found Frames: " + n);
        int width = (int) d.getFrameSize().getWidth();
        int height = (int) d.getFrameSize().getHeight();
        Captcha merged = new Captcha(width, height);
        merged.setOwner(this);
        Captcha tmp;

        for (int i = 0; i < n; i++) {
            BufferedImage frame = d.getFrame(i);
            tmp = new Captcha(width, height);
            tmp.setOwner(this);
            PixelGrabber pg = new PixelGrabber(frame, 0, 0, width, height, false);
            try {
                pg.grabPixels();
            } catch (Exception e) {
                e.printStackTrace();
            }
            ColorModel cm = pg.getColorModel();
            tmp.setColorModel(cm);

            if (!(cm instanceof IndexColorModel)) {
                // not an indexed file (ie: not a gif file)
                tmp.setPixel((int[]) pg.getPixels());
            } else {

                tmp.setPixel((byte[]) pg.getPixels());
            }
            merged.concat(tmp);

        }

        merged.crop(6, 12, 6, 12);
        // merged.removeSmallObjects(0.3, 0.3);
        // merged.invert();
        merged.toBlackAndWhite(0.4);
        merged.removeSmallObjects(0.3, 0.3, 20);
        merged.reduceBlackNoise(4, 0.45);
        merged.toBlackAndWhite(1);
        // merged.reduceBlackNoise(6, 1.6);
        // merged.reduceBlackNoise(6, 1.6);
        // getJas().setBackgroundSampleCleanContrast(0.1);
        // merged.cleanBackgroundBySample(4, 4, 7, 7);

        BasicWindow.showImage(merged.getImage(4), 160, 60);

    }

    /**
     * Debug Methode. Zeigt den Captcha in verschiedenen bearbeitungsstadien an
     * 
     * @param captchafile
     */
    public void showPreparedCaptcha(File captchafile) {

        if (!captchafile.exists()) {
            logger.severe(captchafile.getAbsolutePath() + " existiert nicht");
            return;
        }

        Image captchaImage;
        // if (!this.getImageType().equalsIgnoreCase("jpg")) {
        // captchafile=UTILITIES.toJPG(captchafile);
        // captchaImage = UTILITIES.loadImage(captchafile);
        // logger.info("Bild umgewandelt: "+captchafile.getAbsolutePath());
        // captchafile.delete();
        // } else {
        captchaImage = UTILITIES.loadImage(captchafile);
        // }

        if (frame3 != null) {
            frame3.destroy();
        }
        frame3 = BasicWindow.showImage(captchaImage, "Captchas");
        frame3.add(new JLabel("ORIGINAL"), UTILITIES.getGBC(2, 0, 2, 2));
        frame3.setLocationByScreenPercent(50, 70);
        Captcha captcha = createCaptcha(captchaImage);
        frame3.add(new ImageComponent(captcha.getImage(3)), UTILITIES.getGBC(0, 2, 2, 2));
        frame3.add(new JLabel("Farbraum Anpassung"), UTILITIES.getGBC(2, 2, 2, 2));
        jas.executePrepareCommands(captcha);

        frame3.add(new ImageComponent(captcha.getImage(3)), UTILITIES.getGBC(0, 4, 2, 2));
        frame3.add(new JLabel("Prepare Code ausgeführt"), UTILITIES.getGBC(2, 4, 2, 2));

        // Hole die letters aus dem neuen captcha
        Letter[] letters = captcha.getLetters(letterNum);
        // UTILITIES.wait(40000);
        // prüfe auf Erfolgreiche Lettererkennung
        if (letters == null) {

            logger.severe("2. Lettererkennung ist fehlgeschlagen!");

            return;

        }

        frame3.add(new ImageComponent(captcha.getImageWithGaps(1)), UTILITIES.getGBC(0, 6, 2, 2));
        frame3.add(new JLabel("Buchstaben freistellen"), UTILITIES.getGBC(2, 6, 2, 2));
        frame3.pack();
        frame3.repack();
        if (frame2 != null) {
            frame2.destroy();
        }
        frame2 = new BasicWindow();
        frame2.setTitle("Freigestellte Buchstaben");
        frame2.setLayout(new GridBagLayout());
        frame2.setSize(300, 300);
        frame2.setAlwaysOnTop(true);
        frame2.setLocationByScreenPercent(50, 5);
        frame2.add(new JLabel("Aus Captcha:"), UTILITIES.getGBC(0, 0, 2, 2));

        for (int i = 0; i < letters.length; i++) {
            frame2.add(new ImageComponent(letters[i].getImage(jas.getInteger("simplifyFaktor"))), UTILITIES.getGBC(i * 2 + 2, 0, 2, 2));

        }
        frame2.setVisible(true);
        frame2.pack();
        frame2.setSize(300, frame2.getSize().height);

        // Decoden. checkCaptcha verwendet dabei die gecachte Erkennung der
        // letters
        checkCaptcha(captcha);

        for (int i = 0; i < captcha.getDecodedLetters().length; i++) {
            frame2.add(new JLabel("Aus Datenbank:"), UTILITIES.getGBC(0, 6, 2, 2));
            frame2.add(new ImageComponent(captcha.getDecodedLetters()[i].getImage(jas.getInteger("simplifyFaktor"))), UTILITIES.getGBC(i * 2 + 2, 6, 2, 2));
            frame2.add(new JLabel("Wert:"), UTILITIES.getGBC(0, 8, 2, 2));
            frame2.add(new JLabel(captcha.getDecodedLetters()[i].getDecodedValue()), UTILITIES.getGBC(i * 2 + 2, 8, 2, 2));
            frame2.add(new JLabel("Proz.:"), UTILITIES.getGBC(0, 10, 2, 2));
            frame2.add(new JLabel(captcha.getLetters(getLetterNum())[i].getValityPercent() + "%"), UTILITIES.getGBC(i * 2 + 2, 10, 2, 2));

        }
        frame2.pack();
        frame2.repack();
    }

    private int trainCaptcha(File captchafile, int letterNum) {

        if (!captchafile.exists()) {
            logger.severe(captchafile.getAbsolutePath() + " existiert nicht");
            return -1;
        }
        // Lade das Bild
        Image captchaImage = UTILITIES.loadImage(captchafile);
        // Erstelle hashwert
        String captchaHash = UTILITIES.getLocalHash(captchafile);

        // Prüfe ob dieser captcha schon aufgenommen wurde und überspringe ihn
        // falls ja
        if (isCaptchaInMTH(captchaHash)) {
            logger.fine("Captcha schon aufgenommen" + captchafile);
            return -1;
        }
        // captcha erstellen
        Captcha captcha = createCaptcha(captchaImage);

        String code = null;
        String guess = "";
        // Zeige das OriginalBild
        if (frame3 != null) {
            frame3.destroy();
        }
        frame3 = BasicWindow.showImage(captcha.getImage(2));
        frame3.setLocationByScreenPercent(50, 70);
        // Führe das Prepare aus
        jas.executePrepareCommands(captcha);
        // Hole die letters aus dem neuen captcha
        Letter[] letters = captcha.getLetters(letterNum);
        // UTILITIES.wait(40000);
        // prüfe auf Erfolgreiche Lettererkennung
        if (letters == null) {
            File file = new File(pathMethod + "/detectionErrors1/" + (new Date().getTime()) + "_" + captchafile.getName());
            file.getParentFile().mkdirs();
            captchafile.renameTo(file);
            logger.severe("2. Lettererkennung ist fehlgeschlagen!");
            UTILITIES.wait(5000);
            return -1;

        }
        // Zeige das After-prepare Bild an
        if (frame1 != null) {
            frame1.destroy();
        }
        frame1 = BasicWindow.showImage(captcha.getImageWithGaps(1));
        frame1.setLocationByScreenPercent(50, 30);
        // Zeige die einzellnen letters an
        if (frame2 != null) {
            frame2.destroy();
        }
        frame2 = new BasicWindow();
        frame2.setTitle("Letters");
        frame2.setLayout(new GridBagLayout());
        frame2.setSize(300, 300);
        frame2.setAlwaysOnTop(true);
        frame2.setLocationByScreenPercent(50, 5);
        for (int i = 0; i < letters.length; i++) {
            frame2.add(new ImageComponent(letters[i].getImage(jas.getInteger("simplifyFaktor"))), UTILITIES.getGBC(i * 2, 0, 2, 2));

        }
        frame2.setVisible(true);
        frame2.pack();
        frame2.setSize(300, frame2.getSize().height);

        // Decoden. checkCaptcha verwendet dabei die gecachte Erkennung der
        // letters
        guess = checkCaptcha(captcha);

        for (int i = 0; i < captcha.getDecodedLetters().length; i++) {
            frame2.add(new ImageComponent(captcha.getDecodedLetters()[i].getImage(jas.getInteger("simplifyFaktor"))), UTILITIES.getGBC(i * 2, 6, 2, 2));
            frame2.add(new JLabel(captcha.getDecodedLetters()[i].getDecodedValue()), UTILITIES.getGBC(i * 2, 8, 2, 2));
            frame2.add(new JLabel(captcha.getLetters(getLetterNum())[i].getValityPercent() + "%"), UTILITIES.getGBC(i * 2, 10, 2, 2));

        }
        frame2.pack();
        frame2.repack();
        logger.info("Decoded Captcha: " + guess + " Vality: " + captcha.getValityPercent() + " " + (jas.getDouble("LetterSearchLimitValue")));
        if (captcha.getValityPercent() > 0) {

            if (guess == null) {
                File file = new File(pathMethod + "/detectionErrors2/" + (new Date().getTime()) + "_" + captchafile.getName());
                file.getParentFile().mkdirs();
                captchafile.renameTo(file);
                logger.severe("Letter erkennung fehlgeschlagen");
                return -1;

            }
            if (getCodeFromFileName(captchafile.getName(), captchaHash) == null) {
                if (captcha.getValityPercent() > (jas.getDouble("LetterSearchLimitValue") * 100) || jas.getDouble("LetterSearchLimitValue") <= 0) {

                    code = UTILITIES.prompt("Bitte Captcha Code eingeben (Press enter to confirm " + guess);

                } else {
                    code = guess;
                }
            } else {
                code = getCodeFromFileName(captchafile.getName(), captchaHash);
                logger.warning("captcha code für " + captchaHash + " verwendet: " + code);

            }

        } else {
            logger.info("100% ERkennung.. automatisch übernommen");
            code = guess;
        }
        if (code == null) {
            File file = new File(pathMethod + "/detectionErrors3/" + (new Date().getTime()) + "_" + captchafile.getName());
            file.getParentFile().mkdirs();
            captchafile.renameTo(file);
            logger.severe("Captcha Input error");
            return -1;
        }
        if (code.length() == 0) {
            code = guess;
        }
        if (code.length() != letters.length) {
            File file = new File(pathMethod + "/detectionErrors4/" + (new Date().getTime()) + "_" + captchafile.getName());
            file.getParentFile().mkdirs();
            captchafile.renameTo(file);
            logger.severe("Captcha Input error3");
            return -1;
        }

        String[] oldName = captchafile.getName().split("\\.");
        String ext = oldName[oldName.length - 1];
        String newName = captchafile.getParentFile().getAbsolutePath() + UTILITIES.FS + "captcha_" + this.getMethodDir() + "_code" + code + "." + ext;
        captchafile.renameTo(new File(newName));
        int ret = 0;
        for (int i = 0; i < letters.length; i++) {
            if (letters[i] == null || letters[i].getWidth() < 2 || letters[i].getHeight() < 2) {
                File file = new File(pathMethod + "/detectionErrors5/" + (new Date().getTime()) + "_" + captchafile.getName());
                file.getParentFile().mkdirs();
                captchafile.renameTo(file);
                logger.severe("Letter detection error");
                return -1;
            }
            frame2.add(new ImageComponent(letters[i].getImage()));
            frame2.repack();

            if (code.length() > i && guess.length() > i && code.substring(i, i + 1).equals(guess.substring(i, i + 1))) {
                ret++;

                if (captcha.getDecodedLetters()[i] != null) {
                    captcha.getDecodedLetters()[i].markGood();
                }
                // logger.info(letters[i].getValityPercent()+"%");
                if (!jas.getBoolean("TrainOnlyUnknown") || letters[i].getParent().getValityPercent() >= 30) {

                    letters[i].setOwner(this);
                    letters[i].setTextGrid(letters[i].getPixelString());
                    letters[i].setSourcehash(captchaHash);
                    letters[i].setDecodedValue(code.substring(i, i + 1));
                    letterDB.add(letters[i]);
                    letters[i].printGrid();

                }
            } else {
                if (captcha.getDecodedLetters()[i] != null && letterDB.size() > 30) {
                    captcha.getDecodedLetters()[i].markBad();
                }
                letters[i].setOwner(this);
                letters[i].setTextGrid(letters[i].getPixelString());
                letters[i].setSourcehash(captchaHash);
                letters[i].setDecodedValue(code.substring(i, i + 1));
                letterDB.add(letters[i]);
                letters[i].printGrid();

            }
            // mth.appendChild(element);
        }
        sortLetterDB();
        saveMTHFile();
        return ret;
    }

    private String getCodeFromFileName(String name, String captchaHash) {
        // captcha_share.gulli.com_codeph2.gif

        String[] matches = UTILITIES.getMatches(name, "captcha_°_code°.°");
        if (matches != null && matches.length > 0)
            return matches[1];

        return null;
    }

    /**
     * Sortiert die letterDB Nach den bad Detections. Der Sortieralgo gehört
     * dringend überarbeitet!!! Diese Sortieren hilft die GUten Letter zuerst zu
     * prüfen.
     * 
     * @TODO Sortoer ALGO ändern. zu langsam!!
     */
    private void sortLetterDB() {

        Vector<Letter> ret = new Vector<Letter>();
        for (int i = 0; i < letterDB.size(); i++) {
            Letter akt = letterDB.elementAt(i);
            for (int x = 0; x < ret.size(); x++) {

                if ((akt.getGoodDetections() - 5 * akt.getBadDetections()) > (ret.elementAt(x).getGoodDetections() - 5 * ret.elementAt(x).getBadDetections())) {
                    ret.add(x, akt);
                    akt = null;
                    break;
                }
            }
            if (akt != null)
                ret.add(akt);

        }

        letterDB = ret;

    }

    /**
     * Factory Methode zur Captcha erstellung
     * 
     * @param captchaImage
     *            Image instanz
     * @return captcha
     */
    public Captcha createCaptcha(Image captchaImage) {
        if (captchaImage.getWidth(null) <= 0 || captchaImage.getHeight(null) <= 0) {
            logger.severe("Image Dimensionen zu klein. Image hat keinen Inahlt. Pfad/Url prüfen!");
            return null;
        }
        Captcha ret = Captcha.getCaptcha(captchaImage, this);
        ret.setOwner(this);
        return ret;
    }

    /**
     * Speichert die MTH File
     */
    public void saveMTHFile() {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            // initialize StreamResult with File object to save to file
            StreamResult result = new StreamResult(new StringWriter());
            DOMSource source = new DOMSource(createXMLFromLetterDB());

            transformer.transform(source, result);

            String xmlString = result.getWriter().toString();

            String fileName = pathMethod + "letters.mth";
            logger.fine("Save MTH to " + fileName);
            try {
                fileName = URLDecoder.decode(fileName, "UTF8");
            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            fileName=UTILITIES.URLtoPath(fileName);
            if (!UTILITIES.writeLocalFile(new File(fileName), xmlString)) {
                logger.severe("MTHO file Konnte nicht gespeichert werden");
            }

        } catch (TransformerException e) {
            e.printStackTrace();
        }

    }

    /**
     * Gibt den Captchacode zurück
     * 
     * @param img
     * @param methodPath 
     * @param methodname 
     * @return Captchacode
     */
    public static String getCaptchaCode(Image img, String methodPath, String methodname) {
        JAntiCaptcha jac = new JAntiCaptcha(methodPath, methodname);
        // BasicWindow.showImage(img);
        Captcha cap = jac.createCaptcha(img);
        // BasicWindow.showImage(cap.getImageWithGaps(2));
        String ret = jac.checkCaptcha(cap);
        logger.info("captcha text:" + ret);
        return ret;
    }
    /**
     * Aktualisiert Den Mathod ordner
     * @return Anzahl der aktualisierten Files
     */
    public static int updateMethods(){
        WebUpdater wu= new WebUpdater(UPDATE_PATH);
        wu.setDestPath(JDUtilities.getJDHomeDirectory());
        wu.run();
        return wu.getUpdatedFiles();
    }

    /**
     * Prüft ob der übergeben hash in der MTH file ist
     * 
     * @param captchaHash
     * @return true/false
     */
    private boolean isCaptchaInMTH(String captchaHash) {
        if (letterDB == null)
            return false;

        for (int i = 0; i < letterDB.size(); i++) {
            if (letterDB.elementAt(i).getSourcehash().equals(captchaHash))
                return true;
        }

        return false;

    }

    /**
     * Gibt den erkannten CaptchaText zurück
     * 
     * @param captchafile
     *            Pfad zum Bild
     * @return CaptchaCode
     */
    public String checkCaptcha(File captchafile) {
        logger.finer("check " + captchafile);
        Image captchaImage = UTILITIES.loadImage(captchafile);
        Captcha captcha = createCaptcha(captchaImage);
        // captcha.printCaptcha();
        return checkCaptcha(captcha);
    }

    /**
     * prüft den übergebenen Captcha und gibtd en Code als String zurück das
     * lettersarray des Catchas wird dabei bearbeitet. es werden decoedvalue,
     * avlityvalue und parent gesetzt WICHTIG: Nach dem Decoden eines Captcha
     * herrscht verwirrung. Es stehen unterschiedliche methoden zur verfügung um
     * an bestimmte Informationen zu kommen: captcha.getDecodedLetters() gibt
     * Die letter aus der datenbank zurück. Deren werte sind nicht fest. Auf den
     * Wert von getvalityvalue und getValityPercent kann man sich absolut nicht
     * verlassen. Einzig getDecodedValue() lässt sich zuverlässig auslesen
     * captcha.getLetters() gibt die Wirklichen Letter des captchas zurück. Hier
     * lassen sich alle wichtigen infos abfragen. z.B. ValityValue,
     * ValityPercent, Decodedvalue, etc. Wer immer das hier liest sollte auf
     * keinen fall den fehler machen und sich auf Wert aus dem getdecodedLetters
     * array verlassen
     * 
     * @param captcha
     *            Captcha instanz
     * @return CaptchaCode
     */
    public String checkCaptcha(Captcha captcha) {

        // Führe prepare aus
        jas.executePrepareCommands(captcha);

        Letter[] newLetters = new Letter[this.getLetterNum()];
        String ret = "";
        long correct = 0;
        Letter akt;
        Letter[] letters = captcha.getLetters(getLetterNum());
        if (letters == null) {
            return null;
        }

        if (frame4 != null) {
            // frame4.destroy();
        }
        frame4 = new BasicWindow();
        // frame4.setTitle("Letters");
        // frame4.setLayout(new GridBagLayout());
        // frame4.setSize(300, 300);
        // frame4.setAlwaysOnTop(true);
        // frame4.setLocationByScreenPercent(10, 5);
        counter = 0;

        counterB = 0;
        // frame4.setVisible(true);

        for (int i = 0; i < letters.length; i++) {
            akt = getLetter(letters[i]);
            newLetters[i] = akt;
            if (akt == null) {
                letters[i].setDecodedValue("_");
                letters[i].setValityValue(Integer.MAX_VALUE);
                correct += captcha.getMaxPixelValue();

            } else {
                int letterMapTollerance = jas.getInteger("letterMapTollerance");
                if (letterMap != null && letterMapTollerance < 100 && akt.getValityPercent() > jas.getInteger("ignoreLetterMapLimit")) {
                    jas.set("letterMapTollerance", 100);
                    Letter old = akt;
                    akt = getLetter(letters[i]);
                    logger.info("Erkennung nicht ausreichend genau." + old.getDecodedValue() + " : " + old.getValityPercent() + "% LetterMap deaktiviert Neu:" + akt.getDecodedValue() + " : " + akt.getValityPercent() + "% ");
                    jas.set("letterMapTollerance", letterMapTollerance);
                    newLetters[i] = akt;
                }
                letters[i].setParent(akt);
                letters[i].setDecodedValue(akt.getDecodedValue());
                letters[i].setValityValue(akt.getValityValue());
                correct += akt.getValityValue();

                // frame4.add(new ImageComponent(letters[i].getImage()),
                // UTILITIES.getGBC(counterB * 12 + 0, counter * 2 - 2, 2, 2));
                // frame4.repack();
                // frame4.pack();
            }
            logger.finer("Validty: " + correct);
            if (newLetters[i] != null) {
                ret += akt.getDecodedValue();
            }
            if (letters[i].getWidth() > 0 && letters[i].getHeight() > 0) {
                // BasicWindow.showImage(letters[i].getImage(1), "cap " + i);
                // letters[i].printGrid();+
            }
        }

        captcha.setDecodedLetters(newLetters);
        logger.finer("Correct: " + correct + " - " + ((int) (correct / letters.length)));
        captcha.setValityValue((int) (correct / letters.length));

        return ret;
    }

    /**
     * Vergleicht a und b und gibt eine vergleichszahl zurück. Dabei werden a
     * und b gegeneinander verschoben
     * 
     * @param a
     *            Original Letter
     * @param b
     *            Vergleichsletter
     * @return int 0(super)-0xffffff (ganz übel)
     */
    private int scanCompare(Letter a, Letter b) {
        // int difX = Math.abs(a.getWidth() - b.getWidth());
        // int difY = Math.abs(a.getHeight() - b.getHeight());
        int bx, by, va, vb;
        long value = 0;
        int bestValue = Integer.MAX_VALUE;
        int pixel;

        int scanXFrom = Math.min(b.getWidth() - a.getWidth() - jas.getInteger("scanVariance"), -jas.getInteger("scanVariance"));
        int scanXTo = Math.max(-jas.getInteger("scanVariance"), a.getWidth() - b.getWidth() + jas.getInteger("scanVariance"));

        int scanYFrom = Math.min(b.getHeight() - a.getHeight() - jas.getInteger("scanVariance"), -jas.getInteger("scanVariance"));
        int scanYTo = Math.max(-jas.getInteger("scanVariance"), a.getHeight() - b.getHeight() + jas.getInteger("scanVariance"));
        double maxArea = Math.max(a.getWidth(), b.getWidth()) * Math.max(a.getHeight(), b.getHeight());
        double localArea = 0.0;
        double areaPercent = 0.0;
        // logger.info(scanXFrom+" xto "+scanXTo+" - "+scanYFrom+" yto
        // "+scanYTo);
        int quickFiltered = 0;
        for (int xx = scanXFrom; xx <= scanXTo; xx++) {
            for (int yy = scanYFrom; yy <= scanYTo; yy++) {
                bx = Math.max(0 - xx, 0);
                by = Math.max(0 - yy, 0);
                pixel = 0;
                value = 0;

                // quickscan
                if (getJas().getInteger("quickScan") > 1) {
                    for (int x = 0; x < Math.min(a.getWidth(), b.getWidth() - bx); x += 1 + Math.round(Math.random() * getJas().getInteger("quickScan"))) {
                        for (int y = 0; y < Math.min(a.getHeight(), b.getHeight() - by); y += 1) {
                            va = a.getPixelValue(x, y);
                            vb = b.getPixelValue(x + bx, y + by);
                            pixel++;
                            value += Math.abs(va - vb);
                            // logger.info(va+" -"+vb+" : "+Math.abs(va - vb));
                        }
                    }
                    if (pixel > 0) {
                        value /= pixel;
                        if (value >= (int) (getJas().getDouble("QuickScanFilter") * PixelGrid.getMaxPixelValue(this))) {
                            quickFiltered++;
                            // logger.info("Quickscan filter: "+value+" -
                            // "+(getJas().getDouble("QuickScanFilter")+PixelGrid.getMaxPixelValue(this)));
                            continue;
                        }

                    } else {
                        continue;
                    }
                }
                // logger.info(pixel + " Check it " + value + " - " + (int)
                // (getJas().getDouble("QuickScanFilter") *
                // PixelGrid.getMaxPixelValue(this)));

                pixel = 0;
                value = 0;

                for (int x = 0; x < Math.min(a.getWidth(), b.getWidth() - bx); x++) {
                    for (int y = 0; y < Math.min(a.getHeight(), b.getHeight() - by); y++) {
                        va = a.getPixelValue(x, y);
                        vb = b.getPixelValue(x + bx, y + by);
                        pixel++;
                        value += Math.abs(va - vb);
                    }
                }
                if (pixel > 0) {
                    value /= pixel;
                    localArea = Math.min(a.getWidth(), b.getWidth() - bx) * Math.min(a.getHeight(), b.getHeight() - by);

                    areaPercent = 1.0 - (localArea / maxArea);
                    // Verschlechtert das ergebniss wenn die flächen nicht
                    // zueinander passen

                    value += (a.getMaxPixelValue() - value) * areaPercent * 0.75;

                    // verschlechtert das ergebniss wenn b sehr viele schlechte
                    // erkennungen im Training hatte
                    if (b.getBadDetections() > 0) {
                        value += (a.getMaxPixelValue() - value) * (b.getBadDetections() / (b.getGoodDetections() + b.getBadDetections()));

                    }// value=(int)(value+areaPercent)/2;
                    int tmp = bestValue;
                    bestValue = Math.min((int) value, bestValue);

                    if (bestValue < tmp) {
                        // logger.info("GOOD: "+localArea+"/"+maxArea+" -
                        // "+areaPercent+" - "+value);
                    }
                }

            }
        }

        return bestValue;
    }

    /**
     * Sucht in der MTH ANch dem besten übereinstimmenem letter
     * 
     * @param letter
     *            (refferenz)
     * @return Letter. Beste Übereinstimmung
     */
    private Letter getLetter(Letter letter) {
        long startTime = (new Date()).getTime();
        long bestValue = Long.MAX_VALUE;
        String bestResult = "_";
        String lastResult = "";
        Letter res = new Letter();
        int value;
        res.setOwner(this);
        res.setValityValue(Integer.MAX_VALUE);

        try {

            if (letterDB == null) {
                logger.severe("letterDB nicht vorhanden");
                return null;
            }
       
            Letter tmp;

            for (int i = 0; i < letterDB.size(); i++) {
                tmp = letterDB.elementAt(i);
          
                if (Math.abs(tmp.getHeight() - letter.getHeight()) > jas.getInteger("borderVariance") || Math.abs(tmp.getWidth() - letter.getWidth()) > jas.getInteger("borderVariance")) {
                    continue;
                }
                if (letterMap != null && bestValue < Long.MAX_VALUE) {
                    if (Math.abs(PixelGrid.getValityPercent((int) bestValue, this) - letterMap[res.id][tmp.id]) > jas.getInteger("letterMapTollerance")) {
                        continue;
                    }
                }
                value = scanCompare(letter, tmp);

           
                if (value < bestValue) {
                    bestValue = value;
                    bestResult = tmp.getDecodedValue();
                    res = tmp;
             
                    counter++;
                    if (counter > 40) {
                        counter = 0;
                        counterB += 2;
                    }
                    logger.finer(" New Best value: " + bestResult + " (" + bestValue + ") " + res.getValityPercent() + " good:" + tmp.getGoodDetections() + " bad: " + tmp.getBadDetections());
                    if (jas.getDouble("LetterSearchLimitValue") >= 0 && (value == 0 || (value <= jas.getDouble("LetterSearchLimitValue") * tmp.getMaxPixelValue() && bestResult.equals(lastResult)))) {
                        res = tmp;
                        tmp.setValityValue(value);
                        logger.finer(" Perfect Match: " + bestResult + " (" + bestValue + ") " + res.getValityPercent() + " good:" + tmp.getGoodDetections() + " bad: " + tmp.getBadDetections());
                        return tmp;
                    }
                }

                lastResult = bestResult;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.finer(" Normal Match: " + bestResult + " (" + bestValue + ") " + res.getValityPercent() + " good:" + res.getGoodDetections() + " bad: " + res.getBadDetections());
        logger.fine("Letter erkannt in: " + ((new Date()).getTime() - startTime) + " ms");
        return res;

    }

    /**
     * Liest den captchaornder aus
     * 
     * @param path
     * @return File Array
     */
    private File[] getImages(String path) {
        File dir = new File(path);

        if (dir == null || !dir.exists()) {
            logger.severe("Image dir nicht gefunden " + path);
            return null;
        }

        File[] entries = dir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                // logger.info(pathname.getName());
                if (pathname.getName().endsWith(".jpg") || pathname.getName().endsWith(".png") || pathname.getName().endsWith(".gif")) {

                    return true;
                } else {
                    return false;
                }
            }

        });
        return entries;

    }

    /**
     * @return the imageType
     */
    public String getImageType() {
        return imageType;
    }

    /**
     * @param imageType
     *            the imageType to set
     */
    public void setImageType(String imageType) {
        logger.finer("SET PARAMETER: [imageType] = " + imageType);
        this.imageType = imageType;
    }

    /**
     * @return the letterNum
     */
    public int getLetterNum() {
        return letterNum;
    }

    /**
     * @param letterNum
     *            the letterNum to set
     */
    public void setLetterNum(int letterNum) {
        logger.finer("SET PARAMETER: [letterNum] = " + letterNum);
        this.letterNum = letterNum;
    }

    /**
     * @return the method
     */
    public String getMethodDir() {
        return methodDir;
    }

    /**
     * @param methodPath 
     * @param methodName 
     *     
     */
    public void setMethod(String methodPath, String methodName) {
        logger.finer("SET PARAMETER: [method] = " + methodDir);

        this.methodDir = methodName;

    }

    /**
     * @return the methodAuthor
     */
    public String getMethodAuthor() {
        return methodAuthor;
    }

    /**
     * @param methodAuthor
     *            the methodAuthor to set
     */
    public void setMethodAuthor(String methodAuthor) {
        logger.finer("SET PARAMETER: [methodAuthor] = " + methodAuthor);
        this.methodAuthor = methodAuthor;
    }

    /**
     * @return the methodName
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * @param methodName
     *            the methodName to set
     */
    public void setMethodName(String methodName) {
        logger.finer("SET PARAMETER: [methodName] = " + methodName);
        this.methodName = methodName;
    }

    /**
     * @return the resultFile
     */
    public String getResultFile() {
        return resultFile;
    }

    /**
     * @param resultFile
     *            the resultFile to set
     */
    public void setResultFile(String resultFile) {
        logger.finer("SET PARAMETER: [resultFile] = " + resultFile);
        this.resultFile = resultFile;
    }

    /**
     * @return the sourceImage
     */
    public String getSourceImage() {
        return sourceImage;
    }

    /**
     * @param sourceImage
     *            the sourceImage to set
     */
    public void setSourceImage(String sourceImage) {
        logger.finer("SET PARAMETER: [sourceImage] = " + sourceImage);
        this.sourceImage = sourceImage;
    }

    /**
     * Führt diverse Tests durch. file dient als testcaptcha
     * 
     * @param file
     */
    public void runTestMode(File file) {
        Image img = UTILITIES.loadImage(file);
        Captcha captcha = createCaptcha(img);

        BasicWindow.showImage(captcha.getImage(2), "Original bild");
        captcha.testColor();
        BasicWindow.showImage(captcha.getImage(2), "Farbtester. Bild sollte Identisch sein");

        BasicWindow.showImage(captcha.getImage(2));
        // jas.setBackgroundSampleCleanContrast(0.15);
        // captcha.crop(80, 0, 0, 14);
        captcha.cleanBackgroundByColor(14408167);
        // captcha.reduceWhiteNoise(1);
        // captcha.toBlackAndWhite(0.9);

        BasicWindow.showImage(captcha.getImage(2));

        Vector<PixelObject> letters = captcha.getBiggestObjects(4, 200, 0.7, 0.8);
        for (int i = 0; i < letters.size(); i++) {
            PixelObject obj = letters.elementAt(i);
            // BasicWindow.showImage(obj.toLetter().getImage(3));

            Letter l = obj.toLetter();
            l.removeSmallObjects(0.3, 0.5);

            l = l.align(0.5, -45, +45);

            // BasicWindow.showImage(l.getImage(3));
            l.reduceWhiteNoise(2);
            l.toBlackAndWhite(0.6);
            BasicWindow.showImage(l.getImage(1));

        }

        // captcha.crop(13,8,33,8);
        // BasicWindow.showImage(captcha.getImage(3));
        // captcha.blurIt(5);

        //
        // executePrepareCommands(captcha);

        // BasicWindow.showImage(captcha.getImage(3),"With prepare Code");

    }

    /**
     * @return JACscript Instanz
     */
    public JACScript getJas() {
        return jas;
    }

    /**
     * Entfernt Buchstaben mit einem schlechetb Bad/Good verhältniss
     */
    public void removeBadLetters() {
        Letter tmp;
        logger.info("aktuelle DB Size: " + letterDB.size());
        for (int i = letterDB.size() - 1; i >= 0; i--) {
            tmp = letterDB.elementAt(i);
            if ((tmp.getGoodDetections() == 0 && tmp.getBadDetections() > 0) || ((double) tmp.getBadDetections() / (double) tmp.getGoodDetections()) >= jas.getDouble("findBadLettersRatio")) {
                logger.info("bad Letter entfernt: " + tmp.getDecodedValue() + " (" + tmp.getBadDetections() + "/" + tmp.getGoodDetections() + ")");
                letterDB.removeElementAt(i);
            }

        }
        logger.info("neue DB Size: " + letterDB.size());

        sortLetterDB();
        saveMTHFile();

    }

    /**
     * Erzeugt eine LetterMap. Sie wird zur Geschwindigkeitsoptimierung eingesetzt
     */
    public void createLetterMap() {
        letterMap = new int[letterDB.size()][letterDB.size()];
        int value;
        Letter xLetter;
        Letter yLetter;
        this.sortLetterDB();

        for (int x = 0; x < letterDB.size(); x++) {

            for (int y = 0; y < letterDB.size(); y++) {
                xLetter = letterDB.elementAt(x);
                yLetter = letterDB.elementAt(y);
                value = scanCompare(xLetter, yLetter);
                letterMap[x][y] = PixelGrid.getValityPercent(value, this);
            
     logger.fine           logger.fine("Db Create: " + ((x * 100) / letterDB.size()) + "%");
        }

    }
/**
 * 
 * @return gibt die Lettermap als String zurück
 */
    private String getLetterMapString() {
        StringBuffer ret = new StringBuffer();
        int i = 0;
        for (int x = 0; x < letterMap.length; x++) {
            ret.append("|");
            i++;
            for (int y = 0; y < letterMap[0].length; y++) {

                ret.append(letterMap[x][y]);
                i++;
                ret.append(",");
                i++;
            }
            ret.deleteCharAt(ret.length() - 1);
            logger.fine("Db String: " + ((x * 100) / letterDB.size()) + "%");
        }
        ret.deleteCharAt(0);
        return ret.toString();

    }
}