package org.goobs.testing;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.goobs.exec.ScriptRunner;
import org.goobs.utils.Utils;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class MTScorer {
	
	private static final class ScorerException extends RuntimeException{
		private static final long serialVersionUID = 6251426304678028583L;

		public ScorerException(Throwable e){
			super(e);
		}
	}
	
	private static final class MTDocument{
		private String docID;
		private List<String[][][]> src = new LinkedList<String[][][]>();
		private List<String[][][]> ref = new LinkedList<String[][][]>();
		private List<String[][][]> tst = new LinkedList<String[][][]>();
		
		private List<String[][]> srcP = new LinkedList<String[][]>();
		private List<String[][]> refP = new LinkedList<String[][]>();
		private List<String[][]> tstP = new LinkedList<String[][]>();
		
		private String genre = "no_genre";
		
		private MTDocument(String docID){
			this.docID = docID;
		}
		
		private List<String[][][]> byType(String type){
			if(type.equalsIgnoreCase("srcset")){
				return src;
			}else if(type.equalsIgnoreCase("refset")){
				return ref;
			}else if(type.equalsIgnoreCase("tstset")){
				return tst;
			}else{
				throw new IllegalArgumentException("Illegal set type: " + type);
			}
		}
		private boolean isEmpty(){
			return src.size() == 0;
		}
	}
	
	private static final class SentenceSet{
		private String setID;
		private List<MTDocument> docs = new LinkedList<MTDocument>();
		private MTDocument d;
		private int numRefs;
		
		private String srcLang = "unknown_language";
		private String dstLang = "unknown_language";
		private String sysID = "unknown_system";
		
		
		private SentenceSet(String setID,int numRefs, String docID){
			this.setID = setID;
			this.numRefs = numRefs;
			this.d = new MTDocument(docID);
		}
		private void newDocument(String docID){
			docs.add(d);
			d = new MTDocument(docID);
		}
		private boolean isEmpty(){
			return d == null || d.isEmpty();
		}
	}
	
	public static final class Factory{
		private boolean paragraphed = false;
		
		private List<SentenceSet> sentences = new LinkedList<SentenceSet>();
		private SentenceSet s;
		
		private boolean prohibitNewSet = false;
		private boolean prohibitNewDoc = false;
		
		private String srcPath, refPath, tstPath;
		
		public Factory(){
			this("MAIN");
			prohibitNewDoc = true;
		}
		
		public Factory(String setID){
			this(setID, "MAIN");
			prohibitNewSet = true;
		}
		
		public Factory(String setID, String docID){
			this(setID, -1, docID);
		}
		
		public Factory(String setID, int numRefs, String docID){
			s = new SentenceSet(setID, numRefs, docID);
			s.d.docID = docID;
		}
		
		
		private void closeParagraphs(){
			String[][][] srcA = s.d.srcP.toArray(new String[s.d.srcP.size()][][]);
			String[][][] refA = s.d.refP.toArray(new String[s.d.srcP.size()][][]);
			String[][][] tstA = s.d.tstP.toArray(new String[s.d.srcP.size()][][]);
			s.d.src.add(srcA);
			s.d.ref.add(refA);
			s.d.tst.add(tstA);
			s.d.srcP = new LinkedList<String[][]>();
			s.d.refP = new LinkedList<String[][]>();
			s.d.tstP = new LinkedList<String[][]>();
		}
		
		public Factory setSourceLanguage(String language){
			this.s.srcLang = language;
			return this;
		}
		
		public Factory setTargetLanguage(String language){
			this.s.dstLang = language;
			return this;
		}
		
		public Factory setSystemID(String system){
			this.s.sysID = system;
			return this;
		}
		
		public Factory setGenre(String genre){
			this.s.d.genre = genre;
			return this;
		}
		
		public Factory setXMLPaths(String refPath, String tstPath){
			this.refPath = refPath; this.tstPath = tstPath;
			return this;
		}
		
		public Factory setXMLPaths(String srcPath, String refPath, String tstPath){
			this.srcPath = srcPath; this.refPath = refPath; this.tstPath = tstPath;
			return this;
		}
		
		public Factory beginParagraph(){
			if(paragraphed) throw new IllegalStateException("A paragraph is already open");
			paragraphed = true;
			return this;
		}
		public final Factory addSentencePair(String ref, String test){
			return addSentencePair(ref.split(" "), test.split(" "));
		}
		public final Factory addSentencePair(String[] ref, String[] test){
			return addSentenceInfo(new String[]{"NO_SRC"}, new String[][]{ref}, test);
		}
		public final Factory addSentencePair(String[][] ref, String[] test){
			return addSentenceInfo(new String[]{"NO_SRC"}, ref, test);
		}
		public final Factory addSentenceInfo(String[] src, String[] ref, String[] test){
			return addSentenceInfo(src, new String[][]{ref}, test);
		}
		public final Factory addSentenceInfo(String[] src, String[][] ref, String[] test){
			if(s.numRefs < 0){ s.numRefs = ref.length; }
			if(ref.length != s.numRefs) throw new IllegalArgumentException("Cannot have reference sets with different length");
			s.d.srcP.add(new String[][]{src});
			s.d.refP.add(ref);
			s.d.tstP.add(new String[][]{test});
			if(!paragraphed){
				closeParagraphs();
			}
			return this;
		}
		public Factory endParagraph(){
			if(!paragraphed) throw new IllegalStateException("Closing a paragraph that was never opened");
			closeParagraphs();
			paragraphed = false;
			return this;
		}
		
		public Factory newDocument(String docID){
			if(paragraphed) throw new IllegalStateException("Cannot close set while in a paragraph");
			if(prohibitNewDoc) throw new IllegalStateException("Factory created as single-document dataset");
			s.newDocument(docID);
			return this;
			
		}
		
		public Factory newDocumentSet(String setID, String docID){
			return newDocumentSet(setID, -1, docID);
		}
		
		public Factory newDocumentSet(String setID, int numRefs, String docID){
			if(paragraphed) throw new IllegalStateException("Cannot close set while in a paragraph");
			if(prohibitNewSet) throw new IllegalStateException("Factory created as single-document-set dataset");
			sentences.add(s);
			s = new SentenceSet(setID,numRefs, docID);
			return this;
		}
		
		public MTScorer score(){
			if(s.isEmpty()) throw new IllegalStateException("Cannot score without sentences!");
			try {
				s.docs.add(s.d);
				sentences.add(s);
				File src = srcPath == null ? File.createTempFile("MTScorer", "-src.xml") : new File(srcPath);
				File ref = refPath == null ? File.createTempFile("MTScorer", "-ref.xml") : new File(refPath);
				File tst = tstPath == null ? File.createTempFile("MTScorer", "-tst.xml") : new File(tstPath);
				return new MTScorer(sentences.toArray(new SentenceSet[sentences.size()]), src, ref, tst).rescore();
			} catch (IOException e) {
				throw new ScorerException(e);
			}
		}
	}
	
	private SentenceSet[] sets;
	private File src, ref, tst;
	
	private String strictOut;
	private double bleu, nist;
	
	private MTScorer(SentenceSet[] sets, File src, File ref, File tst){
		this.sets = sets;
		this.src = src;
		this.ref = ref;
		this.tst = tst;
	}
	
	public MTScorer rescore(){
		createXML("srcset", src);
		createXML("refset", ref);
		createXML("tstset", tst);
		//(create the file)
		InputStream in = MTScorer.class.getResourceAsStream("mteval-v13a.pl");
		File dst = null;
		try{
			dst = File.createTempFile("mteval-instance",".pl");
		} catch(Exception e){ throw new RuntimeException(e); }
		Utils.fileCopy(in,dst);
		
		//(run the script)
		String out = 
			ScriptRunner.runPerl(dst.getPath(), new String[]{
					"-s", src.getPath(),
					"-r", ref.getPath(),
					"-t", tst.getPath()
				}).car();
		digest(out);
		this.strictOut = out;
		return this;
	}
	
	public double getBleu(){
		return bleu;
	}
	
	public double getNist(){
		return nist;
	}
	
	private void digest(String output){
		if(output == null || output.trim().equals("")){
			throw new IllegalArgumentException("Script did not return proper output: " + output);
		}
		if(!output.contains("BLEU score")) throw new IllegalArgumentException("Script did not report a BLEU score");
		if(!output.contains("NIST score")) throw new IllegalArgumentException("Script did not report a NIST score");
		String nist = output.replaceAll("\\n+"," ").replaceAll(".*NIST score = (.*?) .*", "$1");
		String bleu = output.replaceAll("\\n+"," ").replaceAll(".*BLEU score = (.*?) for system.*", "$1");
		this.bleu = Double.parseDouble(bleu);
		this.nist = Double.parseDouble(nist);
	}
	
	private void createXML(String type, File out){
		try {
			//--Create Root
			DocumentBuilderFactory documentBuilderFactory 
				= DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder 
				= documentBuilderFactory.newDocumentBuilder();
			Document document 
				= documentBuilder.newDocument();
			Element rootElem 
				= document.createElement("mteval");
			document.appendChild(rootElem);
			
//			System.out.println("TYPE: " + type);
			//--For Each Set...
			for(SentenceSet set : sets){
//				System.out.println("\tSET: " + set.setID);
				//(overhead)
				int numRefs = 1;
				if(type.equalsIgnoreCase("refset")) numRefs = set.numRefs;
				//--For Each Reference...
				for(int i=0; i<numRefs; i++){
//					System.out.println("\t\tREF: " + i + " (" + set.docs.size() + " docs)");
					//(set up xml node)
					Element setElem = document.createElement(type);
					setElem.setAttribute("setid", set.setID);
					setElem.setAttribute("srclang", set.srcLang);
					if(!type.equalsIgnoreCase("srcset"))
						setElem.setAttribute("trglang", set.dstLang);
					if(type.equalsIgnoreCase("tstset"))
						setElem.setAttribute("sysid", set.sysID);
					if(type.equalsIgnoreCase("refset"))
						setElem.setAttribute("refid", "ref" + i);
					rootElem.appendChild(setElem);
					//--For Each Document...
					for(MTDocument doc : set.docs){
//						System.out.println("\t\t\tDOC: " + doc.docID);
						//(create xml node)
						Element docElem = document.createElement("doc");
						docElem.setAttribute("docid", doc.docID);
						docElem.setAttribute("genre", doc.genre);
						setElem.appendChild(docElem);
						int id = 1;
						//--For Each Paragraph
						for(String[][][] paragraph : doc.byType(type)){
//							System.out.println("\t\t\t\t<P>");
							//(create xml node)
							Element pElem = document.createElement("p");
							docElem.appendChild(pElem);
							//--For Each Segment
							for(String[][] seg : paragraph){
								//(create xml node)
								Element segElem = document.createElement("seg");
								segElem.setAttribute("id", "" + id);
								id += 1;
								pElem.appendChild(segElem);
								//(get the relevant reference)
								String[] sent = seg[i];
//								System.out.println("\t\t\t\t\tSEG: " + id + " -> " + Arrays.toString(sent));
								//(write the sentence) dear god at long last
								segElem.appendChild( document.createTextNode(array2str(sent)) );
							}
							
						}
						
					}
				}
			}
			//--Dump File
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
	        Transformer transformer = transformerFactory.newTransformer();
	        DOMSource source = new DOMSource(document);
	        StreamResult result =  new StreamResult(out);
	        transformer.transform(source, result);
		} catch (DOMException e) {
			throw new ScorerException(e);
		} catch (ParserConfigurationException e) {
			throw new ScorerException(e);
		} catch (TransformerConfigurationException e) {
			throw new ScorerException(e);
		} catch (TransformerException e) {
			throw new ScorerException(e);
		}
	}
	
	private static final String array2str(String[] array){
		StringBuilder b = new StringBuilder();
		for(String str : array){
			b.append(str).append(" ");
		}
		return b.substring(0, b.length()-1);
	}
	
	@Override
	public String toString(){
		return strictOut;
	}

	@Override
	public boolean equals(Object o){
		if(o instanceof MTScorer){
			return strictOut.equals(((MTScorer) o).strictOut);
		}
		return false;
	}
	
	@Override
	public int hashCode(){
		return strictOut.hashCode();
	}
}
