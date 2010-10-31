package org.goobs.utils;

import java.util.LinkedList;
import java.util.List;

public class Tree <E>{

	private E item;
	private List <Tree <E>> children;
	
	public Tree(){
		this(null, new LinkedList <Tree<E>> ());
	}
	
	public Tree(E item){
		this(item, new LinkedList <Tree <E>> ());
	}
	
	public Tree(E item, List <Tree <E>> children){
		this.item = item;
		this.children = children;
	}
	
	public Tree(E item, E child){
		this.item = item;
		this.children = new LinkedList <Tree<E>> ();
		addChild(child);
	}
	
	public E getItem(){
		return item;
	}
	
	public int childCount(){
		return children.size();
	}

	public Tree <E> getChild(int index){
		return children.get(index);
	}
	
	public void addChild(Tree <E> child){
		children.add(child);
	}

	public void addChild(E child){
		children.add(new Tree <E> (child));
	}
	
	public boolean removeChild(Tree <E> child){
		return children.remove(child);
	}
	
	public boolean isLeaf(){
		return children == null || children.size() == 0;
	}
	
	public Tree <E> copy(){
		List <Tree <E>> children = new LinkedList <Tree <E>> ();
		children.addAll(this.children);
		Tree <E> rtn = new Tree<E>(this.item, children);
		return rtn;
	}
	
	public void prettyPrint(String delimiter){
		prettyPrint(delimiter, 0);
	}
	
	@SuppressWarnings("unchecked")
	private void prettyPrint(String delimiter, int depth){
		if(item != null){
			for(int i=0; i<depth; i++){ System.out.print(delimiter); }
			System.out.println(item);
			if(children != null){
				for(Object child : children){
					if(child instanceof Tree){
						((Tree) child).prettyPrint(delimiter, depth+1);
					}else{
						for(int i=0; i<depth+1; i++){ System.out.print(delimiter); }
						System.out.println(child);
					}
				}
			}
		}
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public boolean equals(Object o){
		if(o instanceof Tree){
			Tree <E> t = (Tree <E>) o;
			if(!t.item.equals(this.item)){
				return false;
			}
			return children == t.children;
		}else{
			return item.equals(o);
		}
	}
	
	@Override
	public int hashCode(){
		return item.hashCode();
	}
	
	@Override
	public String toString(){
		StringBuilder b = new StringBuilder();
		b.append("(").append(this.item).append(" ");
		for(Tree <E> t : this.children ){
			b.append(t.toString());
		}
		b.append(")");
		return b.toString();
	}
}
