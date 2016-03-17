package com.exlibris.dps.delivery.vpp.bookReader;

import gov.loc.mets.MetsType.FileSec.FileGrp;
import gov.loc.mets.StructMapType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Scanner;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.xerces.parsers.DOMParser;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import com.exlibris.core.infra.common.cache.SessionUtils;
import com.exlibris.core.infra.common.exceptions.logging.ExLogger;
import com.exlibris.core.infra.svc.api.CodeTablesResourceBundle;
import com.exlibris.core.sdk.consts.Enum;
import com.exlibris.core.sdk.formatting.DublinCore;
import com.exlibris.core.sdk.parser.IEParserException;
import com.exlibris.digitool.common.dnx.DnxDocument;
import com.exlibris.digitool.common.dnx.DnxDocumentHelper;
import com.exlibris.dps.alto.xsd.Alto;
import com.exlibris.dps.alto.xsd.Alto.Layout.Page;
import com.exlibris.dps.alto.xsd.BlockType;
import com.exlibris.dps.alto.xsd.ComposedBlockType;
import com.exlibris.dps.alto.xsd.StringType;
import com.exlibris.dps.alto.xsd.TextBlockType;
import com.exlibris.dps.alto.xsd.TextBlockType.TextLine;
import com.exlibris.dps.sdk.access.Access;
import com.exlibris.dps.sdk.access.AccessException;
import com.exlibris.dps.sdk.delivery.AbstractViewerPreProcessor;
import com.exlibris.dps.sdk.delivery.SmartFilePath;
import com.exlibris.dps.sdk.deposit.IEParser;

public class BookReaderViewerPreProcessorAlto extends AbstractViewerPreProcessor{
	public enum pageProgression {RTLl,LR}
	public enum hideSearchBar {YES,NO}
	public enum repType {JPEG, JPG, JP2, PNG};
	private static String ALTO_FILE="alto.xml";
	private List<String> imagesPids = new ArrayList<String>();
	private List<String> altoPids = new ArrayList<String>();
	private String pid = null;
	private String imageRepPid = null;
	private String title = "";
	private String ext = null;
	private DnxDocumentHelper fileDocumentHelper = null;
	private String progressBarMessage = null;
	private static final ExLogger logger = ExLogger.getExLogger(BookReaderViewerPreProcessorAlto.class);
	private static final String EXTENSION = "extension";
	Access access;
	private String pagesWight;
	private String pagesHight;
	private String pagesLeaf;
	private String structMapLogical=null;
	private static final String PAGE_WIGHT="pageWight";
	private static final String PAGE_HIGHT="pageHight";
	private static final String LEAF_MAP="leafMap";
	private static final String PROP_FILE_NAM = "book.properties";
	private static final String BOOK_TITLE="bookTitle";
	private Map<String,Integer> filesMap = new HashMap<String, Integer>();


	//This method will be called by the delivery framework before the call for the execute Method
	@Override
	public void init(DnxDocument dnx, Map<String, String> viewContext, HttpServletRequest request, String dvs,String ieParentId, String repParentId)
			throws AccessException {
 		super.init(dnx, viewContext, request, dvs, ieParentId, repParentId);
 		StructMapType[] structMapAltoTypeArray = null;
 		StructMapType[] structMapImageTypeArray =null;
		this.pid = getPid();
		IEParser ieParser;
		try {
			ieParser = getAccess().getIEByDVS(dvs);
			FileGrp[] repList = ieParser.getFileGrpArray();

			for(FileGrp fg:repList){
				StructMapType[] structMapImageTypeTemp = ieParser.getStructMapsByFileGrpId(fg.getID());
				List<String> Pids = ieParser.getFilesArray(structMapImageTypeTemp[0]);
				String extension = getExt(dvs, ieParser, Pids.get(0));
				if (extension.equalsIgnoreCase("xml")) {
					structMapAltoTypeArray = structMapImageTypeTemp;
				}else if(contains(extension)){
					imageRepPid = fg.getID();
					structMapImageTypeArray = structMapImageTypeTemp;
				}
			}

			for(StructMapType structMapType:structMapImageTypeArray){
				imagesPids = ieParser.getFilesArray(structMapType);

				if(structMapType.getTYPE().equals(Enum.StructMapType.LOGICAL.name())){
					structMapLogical =structMapType.toString();
					break;
				}
			}
			if (null != structMapAltoTypeArray) {
				for (StructMapType structMapType : structMapAltoTypeArray) {
					altoPids = ieParser.getFilesArray(structMapType);

					if (structMapType.getTYPE().equals(Enum.StructMapType.LOGICAL.name())) {
						break;
					}
				}
			}

			ext = getExt(dvs, ieParser, imagesPids.get(0));
			if (!contains(ext)) {
				logger.error("Error In Book Reader VPP - The viewer doesn't support the following ext:"	+ ext.toUpperCase(), pid);
				throw new Exception();
			}

		} catch (Exception e) {
			logger.error("Error In Book Reader VPP - cannot retreive the files to view", e, pid);
			throw new AccessException();
		}
	}

	private String getExt(String dvs, IEParser ieParser,String pid)
			throws IEParserException, Exception {
		String resault;
		DublinCore dc = ieParser.getIeDublinCore();
		this.title = dc.getTitle();
		DnxDocument firstFileDnx = getAccess().getFileInfoByDVS(dvs, pid);
		fileDocumentHelper = new DnxDocumentHelper(firstFileDnx);
		resault = fileDocumentHelper.getGeneralFileCharacteristics().getFileExtension();
		return resault;
	}

	public boolean runASync(){
		return true;
	}

	//Does the pre-viewer processing tasks.
	public void execute() throws Exception {
		Map<String, Object> paramMap = getAccess().getViewerDataByDVS(getDvs()).getParameters();

		//moved from switch-case to else-if because of plugin issues related to including this ENUM class
		if(ext != null && !"".equals(ext) && contains(ext)){
			prepareFiles();
		} else {
			logger.warn("Book reader viewer pre processor doesn't support type: " + ext + ", PID: " + pid);
		}
		// add params to session
        paramMap.put("ie_dvs", getDvs());
        paramMap.put("ie_pid", pid);
        paramMap.put("rep_pid", imageRepPid);
        paramMap.put("pageProgression", pageProgression.LR.toString().toLowerCase());
        paramMap.put("hideSearch", (altoPids.size()>0)?hideSearchBar.NO.toString().toLowerCase():hideSearchBar.YES.toString().toLowerCase());
        if(null != structMapLogical){
			String sideBar = getSideBar();
			paramMap.put("sideBar", sideBar);
		}

        getAccess().setParametersByDVS(getDvs(), paramMap);
        getAccess().updateProgressBar(getDvs(), "", 100);
	}

	private void prepareFiles() throws Exception{
		//export + rename
		String filePath = "";

		if(imagesPids.size()==altoPids.size()){
			for(int i=0;i<imagesPids.size();i++){
				if(null!=structMapLogical){
					filesMap.put(imagesPids.get(i), i);
				}
				filePath = getAccess().exportFileStream(imagesPids.get(i), BookReaderViewerPreProcessorAlto.class.getSimpleName(), ieParentId, repDirName, imageRepPid + File.separator +getFileNumber(i+1));
				filePath = getAccess().exportFileStream(altoPids.get(i), BookReaderViewerPreProcessorAlto.class.getSimpleName(), ieParentId, repDirName, imageRepPid + File.separator +getFileNumber(i+1));
				updateProgressBar(i,imagesPids.size());
			}
		}else{
			int arraySize=imagesPids.size()+altoPids.size();

			for(int i=0;i<imagesPids.size();i++){
				if(null!=structMapLogical){
					filesMap.put(imagesPids.get(i), i);
				}
				filePath = getAccess().exportFileStream(imagesPids.get(i), BookReaderViewerPreProcessorAlto.class.getSimpleName(), ieParentId, repDirName, imageRepPid + File.separator +getFileNumber(i+1));

				updateProgressBar(i,arraySize);
			}
			for(int i=0;i<altoPids.size();i++){
				filePath = getAccess().exportFileStream(altoPids.get(i), BookReaderViewerPreProcessorAlto.class.getSimpleName(), ieParentId, repDirName, imageRepPid + File.separator +getFileNumber(i+1));
				updateProgressBar(imagesPids.size()+i,arraySize);
			}
		}

		String directoryPath = filePath.substring(0, filePath.lastIndexOf(File.separator)+1);
		File altoFile=new File(directoryPath+ALTO_FILE);
		if(altoPids.size()>0 && !altoFile.exists() ){
				Alto fullAlto = joinAltos(directoryPath);
				fullAlto = setParagraphTitle(fullAlto);
				parsAltoToXML(fullAlto,altoFile);
				parsToPropFile(directoryPath);
		}else{
			altoFile=null;
		}

		if(altoPids.size()==0){
			createDummyBookProp();
			parsToPropFile(directoryPath);
		}



		getAccess().setFilePathByDVS(getDvs(), new SmartFilePath(directoryPath), imageRepPid);
	}



	private void updateProgressBar(int index,int arraySize) throws Exception{
		if((index % 10) == 0){
			if(progressBarMessage == null){
				Locale locale = new Locale(SessionUtils.getSessionLanguage());
				ResourceBundle resourceBundle = CodeTablesResourceBundle.getDefaultBundle(locale);
				progressBarMessage = resourceBundle.getString("delivery.progressBar.bookReaderMessage");
			}
			getAccess().updateProgressBar(getDvs(), progressBarMessage, Integer.valueOf((index*100)/arraySize));
		}
	}

	private boolean contains(String extension) {
	    for (repType rt : repType.values()) {
	        if (rt.name().equalsIgnoreCase(extension)) {
	        	return true;
	        }
	    }
	    return false;
	}

	private void createDummyBookProp() {
		StringBuilder sbWight = new StringBuilder("[");
		StringBuilder sbHight = new StringBuilder("[");
		StringBuilder sbLeaf = new StringBuilder("[");
		for(int i=0; i<imagesPids.size();i++){
			sbWight.append("1024").append(",");
			sbHight.append("800").append(",");
			sbLeaf.append(i+1).append(",");
		}
		sbWight.deleteCharAt(sbWight.length()-1);
		sbHight.deleteCharAt(sbHight.length()-1);
		sbLeaf.deleteCharAt(sbLeaf.length()-1);
		sbWight.append("]");
		sbHight.append("]");
		sbLeaf.append("]");
		pagesWight =sbWight.toString();
		pagesHight =sbHight.toString();
		pagesLeaf =sbLeaf.toString();
	}

	private Alto joinAltos(String directoryPath) {
		File altoXMLfolder = new File(directoryPath);
		ArrayList<File> allXMLFiles = getFilesFromFolder(altoXMLfolder);
		Alto templet = getAltoFromFile(allXMLFiles.get(0));
		templet.getLayout().getPage().clear();
			StringBuilder sbWight = new StringBuilder("[");
			StringBuilder sbHight = new StringBuilder("[");
			StringBuilder sbLeaf = new StringBuilder("[");
			int i=1;
		for (File file : allXMLFiles) {
			Integer fileNumber = Integer.parseInt(file.getName().substring(0, file.getName().length()-4));
			Alto alto= getAltoFromFile(file);
			Page page = alto.getLayout().getPage().get(0);
			page.setID(fileNumber.toString());
			templet.getLayout().getPage().add(page);
			sbWight.append(page.getWIDTH()).append(",");
			sbHight.append(page.getHEIGHT()).append(",");
			sbLeaf.append(i).append(",");
			i++;
		}
		sbWight.deleteCharAt(sbWight.length()-1);
		sbHight.deleteCharAt(sbHight.length()-1);
		sbLeaf.deleteCharAt(sbLeaf.length()-1);
		sbWight.append("]");
		sbHight.append("]");
		sbLeaf.append("]");
		pagesWight =sbWight.toString();
		pagesHight =sbHight.toString();
		pagesLeaf =sbLeaf.toString();
		return templet;
	}

	public ArrayList<File> getFilesFromFolder(File folder) {
		ArrayList<File> aList = new ArrayList<File>();

		File[] files = folder.listFiles();
		for (File file : files) {

			if (file.isFile() && file.getName().endsWith("xml")) {
				aList.add(file);
			}
		}
		return aList;
	}

	private Alto getAltoFromFile(File file) {
		Alto alto = null;

		try {
			JAXBContext jc = JAXBContext.newInstance(Alto.class);
			Unmarshaller unmarshaller = jc.createUnmarshaller();

			// Create the XMLReader
			SAXParserFactory factory = SAXParserFactory.newInstance();
			XMLReader reader = factory.newSAXParser().getXMLReader();

			// The filter class to set the correct namespace
			XMLFilterImpl xmlFilter = new XMLNamespaceFilter(reader);
			reader.setContentHandler(unmarshaller.getUnmarshallerHandler());
			InputStream inStream = new FileInputStream(file);

			SAXSource source = new SAXSource(xmlFilter, new InputSource(inStream));


			alto = (Alto) unmarshaller.unmarshal(source);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return alto;
	}

	private void parsAltoToXML(Alto altoTemp, File file) {
		try {
			JAXBContext jaxbContext = JAXBContext.newInstance(Alto.class);
			Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

			// output pretty printed
			jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

			jaxbMarshaller.marshal(altoTemp, file);

		} catch (JAXBException e) {
			e.printStackTrace();
		}
	}

	private String getFileNumber(int i) {
		String resault="";
		switch (String.valueOf(i).length()) {
		  case 1:  resault = "000"+i;
          break;
		  case 2:  resault = "00"+i;
          break;
		  case 3:  resault = "0"+i;
          break;
		  case 4:  resault = Integer.toString(i);
          break;
		  default : resault = Integer.toString(i);
		  }
		return resault;
	}
//Added support for composedBlock
	private Alto setParagraphTitle(Alto alto) {
		for (Page page : alto.getLayout().getPage()) {
			for (BlockType blockType : page.getPrintSpace().getTextBlockOrIllustrationOrGraphicalElement()) {
				if (blockType instanceof TextBlockType) {
					titleBuilder(blockType);
					
				} else if (blockType instanceof ComposedBlockType) {
					ComposedBlockType composedBlockType=(ComposedBlockType)blockType;
					for (BlockType innerBlockType : composedBlockType.getTextBlockOrIllustrationOrGraphicalElement()) {
						if (innerBlockType instanceof TextBlockType) {
							titleBuilder(innerBlockType);
						}
					}
				}
			}
		}
		return alto;
	}

	private void titleBuilder(BlockType blockType) {
		TextBlockType textBlockType = (TextBlockType) blockType;
		StringBuilder paragraph= new StringBuilder();
		for (TextLine textLine : textBlockType.getTextLine()) {
			for (Object object : textLine.getStringAndSP()) {
				if (object instanceof StringType) {
					StringType stringType = (StringType) object;
					paragraph.append(stringType.getCONTENT()).append(" ");
				}
			}
			paragraph.deleteCharAt(paragraph.length()-1).append("\n");
		}
		String par = paragraph.toString().replaceAll("-\n", "");
		textBlockType.setTitle(par);
	}


	private void parsToPropFile(String filePath){
		Properties prop = new Properties();

    	try {
    		prop.setProperty(EXTENSION, ext.toLowerCase());
    		prop.setProperty(PAGE_WIGHT, pagesWight);
    		prop.setProperty(PAGE_HIGHT, pagesHight);
    		prop.setProperty(LEAF_MAP, pagesLeaf);
			if (null != title && !"".equals(title)) {
				prop.setProperty(BOOK_TITLE, title);
			} else {
				prop.setProperty(BOOK_TITLE, "No Title");
			}

    		prop.store(new FileOutputStream(filePath+PROP_FILE_NAM), null);

    	} catch (IOException ex) {
    		ex.printStackTrace();
        }
	}

	private String getSideBar(){

        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("PLUGIN-INF/transformer_logical.xsl");

        String xslFileContent = new Scanner(inputStream,"UTF-8").useDelimiter("\\A").next();

        String result=runXSLTransformer(structMapLogical,xslFileContent);
        result = result.replaceAll("\"", "'");
		for(String pid:imagesPids){
			result  = result .replaceAll(pid, filesMap.get(pid).toString());
		}

		return result;
	}

	public static String runXSLTransformer(String xml, String xslFileContent) {
		if (xml == null || xslFileContent == null) {
			return null;
		}
		xml = fixXML(xml);
		StringWriter outputXML=null;
		try {
			DOMParser parser = new DOMParser();
			InputSource inputSource = new InputSource(new StringReader(xml));
			parser.parse(inputSource);
			org.w3c.dom.Document doc = parser.getDocument();

			Source xmlSource = new DOMSource(doc);

			outputXML = new StringWriter();
			Result result = new StreamResult(outputXML);
			Source xslSource = new StreamSource(new StringReader(xslFileContent));

			Transformer  transformer = TransformerFactory.newInstance().newTransformer(xslSource);
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
//			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
//			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	        transformer.transform(xmlSource, result);
		} catch (Exception e) {
			return null;
		}

		return outputXML.toString();
	}

	private static String fixXML(String xml) {
		xml = xml.replaceAll("mets:fptr", "fptr");
		xml = xml.replaceAll("mets:div", "div");
		xml = xml.replaceAll("mets:structMap", "structMap");
		return xml;
	}

}
