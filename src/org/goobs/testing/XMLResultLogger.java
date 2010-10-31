package org.goobs.testing;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.goobs.exec.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class XMLResultLogger extends ResultLogger{

	private String indexPath;
	private String path;
	
	private static final class Instance{
		private Object gold;
		private Object guess;
		private HashMap <String,Double> values = new HashMap <String,Double>();
		private HashMap <String,String> strings = new HashMap <String,String>();
		private Instance(Object guess, Object gold){
			this.gold = gold; this.guess = guess;
		}
	}

	private HashMap <Integer, Instance> instances = new HashMap <Integer, Instance> ();
	private HashMap <String, Double> globalValues = new HashMap <String, Double> ();
	private HashMap <String,String> globalStrings = new HashMap <String,String>();
	
	public XMLResultLogger(String path, String indexPath){
		this.indexPath = indexPath;
		this.path = path;
	}
	
	@Override
	public void add(int index, Object guess, Object gold) {
		instances.put(index, new Instance(guess, gold));
	}

	@Override
	public String getIndexPath() {
		return indexPath;
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public void setGlobalResult(String name, double value) {
		globalValues.put(name, value);
	}

	@Override
	public void setLocalResult(int index, String name, double value) {
		Instance instance = instances.get(index);
		if(instance == null){
			throw new IllegalArgumentException("Must add an instance before setting it's results");
		}
		instance.values.put(name, value);
	}
	
	@Override
	public void addGlobalString(String name, Object value) {
		globalStrings.put(name, value.toString());
	}

	@Override
	public void addLocalString(int index, String name, Object value) {
		Instance instance = instances.get(index);
		if(instance == null){
			throw new IllegalArgumentException("Must add an instance before setting it's results");
		}
		instance.strings.put(name, value.toString());
	}

	@Override
	public void save(String root, boolean shouldIndex) {
		if(shouldIndex){
			super.appendToIndex(globalValues);
		}

		try {
			//--Create Root
			DocumentBuilderFactory documentBuilderFactory 
				= DocumentBuilderFactory.newInstance();
			DocumentBuilder documentBuilder 
				= documentBuilderFactory.newDocumentBuilder();
			Document document 
				= documentBuilder.newDocument();
			Element rootElement 
				= document.createElement(root);
			document.appendChild(rootElement);
			
			//--Global Elements
			//string fields
			String[] atrs = new String[globalStrings.keySet().size()];
			int k=0; for(String key : globalStrings.keySet()){ atrs[k] = key; k+=1; }; Arrays.sort(atrs);
			for(k=0; k<atrs.length; k++){
				Element em = document.createElement(atrs[k]);
				em.appendChild(document.createTextNode(globalStrings.get(atrs[k])));
				rootElement.appendChild(em);
			}
			//global values
			Element global = document.createElement("global_values");
		    rootElement.appendChild(global);
			for(String key : globalValues.keySet()){
				String val = df.format(globalValues.get(key));
				Element em = document.createElement(key);
				em.appendChild(document.createTextNode(val));
				global.appendChild(em);
			}
		    
			//--Instance elements
			//(sort keys)
			Integer[] indexes = new Integer[instances.keySet().size()];
			int index=0;
			for(Integer key : instances.keySet()){
				indexes[index] = key;
				index += 1;
			}
			Arrays.sort(indexes);
			//(add elements)
			for(int i=0; i<indexes.length; i++){
				Instance instance = instances.get(indexes[i]);
				Element elem = document.createElement("instance");
				//set id
				elem.setAttribute("id", indexes[i].toString());
				//set values
				atrs = new String[instance.values.keySet().size()];
				k=0; for(String key : instance.values.keySet()){ atrs[k] = key; k+=1; }; Arrays.sort(atrs);
				for(k=0; k<atrs.length; k++){
					elem.setAttribute(atrs[k], df.format(instance.values.get(atrs[k])));
				}
				//add guess and gold
				Element guess = document.createElement("guess");
				guess.appendChild(document.createTextNode(instance.guess.toString()));
				elem.appendChild(guess);
				Element gold = document.createElement("gold");
				gold.appendChild(document.createTextNode(instance.gold.toString()));
				elem.appendChild(gold);
				//add other string fields
				atrs = new String[instance.strings.keySet().size()];
				k=0; for(String key : instance.strings.keySet()){ atrs[k] = key; k+=1; }; Arrays.sort(atrs);
				for(k=0; k<atrs.length; k++){
					Element field = document.createElement(atrs[k]);
					field.appendChild(document.createTextNode(instance.strings.get(atrs[k])));
					elem.appendChild(field);
				}
				//add node
				rootElement.appendChild(elem);
			}
			
			//--Dump File
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
	        Transformer transformer = transformerFactory.newTransformer();
	        DOMSource source = new DOMSource(document);
	        StreamResult result =  new StreamResult(new File(path));
	        transformer.transform(source, result);
	        
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		} catch (TransformerException e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}
	}

	@Override
	public ResultLogger spawnGroup(String name, int index) {
		throw new IllegalStateException("Groups are not implemented for XML logging yet!");
	}

	@Override
	public void suggestFlush() {
		Log.warn("XML_LOGGER", "Suggested flushes are ignored by the XML Logger");
	}

}
