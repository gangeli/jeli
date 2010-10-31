package org.goobs.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ConcurrentModificationException;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.goobs.io.Console;

public class GuiConsole implements Console {

	private static final int WAIT_NOTHING = 0;
	private static final int WAIT_INITIALIZE = 1;
	private static final int WAIT_INPUT = 2;
	
	private static GuiConsole instance;
	private static Color bg = Color.WHITE;
	private static String idleString = "[no input required]";

	private JTextArea textArea;
	private JScrollPane pane;
	private JLabel inputLabel;
	private JTextField inputArea;
	
	private int waitingFor = WAIT_NOTHING;
	private boolean hasDied = false;
	
	private boolean readStop;
	private char readStopChar;
	private int readChars = 0;
	private int readLen = -1;
	
	
	public static class ConsoleDiedException extends RuntimeException{
		/**
		 * 
		 */
		private static final long serialVersionUID = -1811797909500176333L;
	}
	
	private GuiConsole() {

	}

	
	
	/*
	 * Public methods
	 */
	public void println(Object o) {
		printAndUpdate(o.toString() + "\n");
	}
	public void println(int x){
		printAndUpdate("" + x + "\n");
	}
	public void println(double x){
		printAndUpdate("" + x + "\n");
	}
	public void println(boolean x){
		printAndUpdate("" + x + "\n");
	}
	public void println(long x){
		printAndUpdate("" + x + "\n");
	}
	public void print(Object o) {
		printAndUpdate(o.toString());
	}
	public void print(int x){
		printAndUpdate("" + x);
	}
	public void print(double x){
		printAndUpdate("" + x);
	}
	public void print(boolean x){
		printAndUpdate("" + x);
	}
	public void print(long x){
		printAndUpdate("" + x);
	}
	
	
	public String readUntil(String prompt, int length){
		return read(prompt, false, ' ', length);
	}

	public String readUntil(String prompt, char end) {
		return read(prompt, true, end, -1);
	}
	public String readUntil(String prompt, char end, int maxLength){
		return read(prompt, true, end, maxLength);
	}
	public Integer readInteger(String prompt){
		String read = read(prompt, true, '\n', -1);
		try {
			return Integer.parseInt(read);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	public Double readDouble(String prompt){
		String read = read(prompt, true, '\n', -1);
		try {
			return Double.parseDouble(read);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	public Long readLong(String prompt){
		String read = read(prompt, true, '\n', -1);
		try {
			return Long.parseLong(read);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	public Boolean readBoolean(String prompt){
		String read = read(prompt, true, '\n', -1);
		try {
			return Boolean.parseBoolean(read);
		} catch (NumberFormatException e) {
			return null;
		}
	}
	public String readLine(String prompt){
		return readUntil(prompt, '\n');
	}
	public String readLine(){
		return readUntil("input?", '\n');
	}
	public Integer readInteger(){
		return readInteger("Integer?");
	}
	public Double readDouble(){
		return readDouble("Double?");
	}
	public Long readLong(){
		return readLong("Long?");
	}
	public Boolean readBoolean(){
		return readBoolean("Boolean?");
	}
	
	
	public boolean isShowing(){
		return !this.hasDied;
	}
	
	
	/*
	 * Actual implementations
	 */
	
	private synchronized String read(String prompt, boolean shouldEnd, char end, int maxLength){
		//--Overhead
		if(waitingFor != WAIT_NOTHING){
			throw new ConcurrentModificationException("Reading from console while busy (already reading?)");
		}
		if(hasDied){
			throw new ConsoleDiedException();
		}
		try {
			//(give other threads a chance to clear stuff up)
			Thread.sleep(50);
		} catch (InterruptedException e) {}
		
		//--Initialize GUI components
		this.inputLabel.setText(prompt);
		this.inputArea.setText("");
		this.inputLabel.setVisible(true);
		this.inputArea.setVisible(true);
		this.textArea.setCaretPosition(this.textArea.getText().length());
		
		//--Set up and wait for event
		this.readStop = shouldEnd;
		this.readStopChar = end;
		this.readLen = maxLength;
		waitFor(WAIT_INPUT);
		this.readStop = false;
		this.readLen = -1;
		
		//--Process GUI components
		String rtn = inputArea.getText();
		this.inputArea.setVisible(false);
		this.inputLabel.setVisible(false);
		this.inputArea.setText("");
		this.inputLabel.setText(idleString);
		return rtn;
	}
	
	private synchronized void printAndUpdate(String str){
		if(hasDied){
			throw new ConsoleDiedException();
		}
		textArea.append(str);
		textArea.setCaretPosition(textArea.getText().length());
	}

	private synchronized void inputReceived(char c){
		if(waitingFor == WAIT_INPUT ){
			readChars = inputArea.getText().length();
			if(readLen >=0 && readChars == readLen){
				available();
			}else if(readStop && c == readStopChar){
				available();
			}
		}
	}
	
	private synchronized void waitFor(int waitFor){
		if(this.waitingFor != WAIT_NOTHING){
			throw new IllegalStateException("Waiting on Console monitor twice: " + waitingFor + ", trying to wait on " + waitFor);
		}
		this.waitingFor = waitFor;
		try {
			this.wait();
		} catch (InterruptedException e) {
			System.out.println("WARNING: wait interrupted for: " + this.waitingFor);
		}
		this.waitingFor = WAIT_NOTHING;
		if(this.hasDied){
			throw new ConsoleDiedException();
		}
	}
	
	private synchronized void available() {
		while(this.waitingFor == WAIT_NOTHING){
			Thread.yield();
		}
		this.notify();
	}
	
	private synchronized void dead(){
		if(this.waitingFor != WAIT_NOTHING){
			this.hasDied = true;
			this.notify();
		}
	}
	
	public synchronized Console show() {
		System.out.print("creating gui console...");
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				createAndShowGUI();
				available();
			}
		});
		waitFor(WAIT_INITIALIZE);
		System.out.println("Done");
		return this;
	}

	private final void createAndShowGUI() {
		this.hasDied = false;
		// Create and set up the window.
		final JFrame frame = new JFrame("Console");
		frame.setBackground(bg);
		frame.getContentPane().setBackground(bg);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.addWindowListener(new WindowListener(){
			@Override
			public void windowActivated(WindowEvent arg0) {			}
			@Override
			public void windowClosed(WindowEvent arg0) {
				dead();
			}
			@Override
			public void windowClosing(WindowEvent arg0) {			}
			@Override
			public void windowDeactivated(WindowEvent arg0) {			}
			@Override
			public void windowDeiconified(WindowEvent arg0) {			}
			@Override
			public void windowIconified(WindowEvent arg0) {			}
			@Override
			public void windowOpened(WindowEvent arg0) {			}
			
		});

		textArea = new JTextArea();
		textArea.setDisabledTextColor(Color.BLACK);
		textArea.setEnabled(false);
		textArea.setBackground(bg);
		pane = new JScrollPane();
		pane.setViewportView(textArea);
		pane.setBorder(null);
		pane.setAutoscrolls(true);
		
		
		inputLabel = new JLabel("[no input required]");
		inputLabel.setBackground(bg);
		inputLabel.setVisible(false);
		inputLabel.setText(idleString);
		
		inputArea = new JTextField();
		inputArea.setBackground(bg);
		inputArea.setVisible(false);
		inputArea.addKeyListener(new KeyListener(){
			@Override
			public void keyPressed(KeyEvent arg0) {
			}
			@Override
			public void keyReleased(KeyEvent arg0) {
			}
			@Override
			public void keyTyped(KeyEvent arg0) {
				inputReceived(arg0.getKeyChar());
			}
		});

        
        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(frame.getContentPane());
        frame.getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 376, Short.MAX_VALUE)
                    .addComponent(inputArea, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 376, Short.MAX_VALUE)
                    .addComponent(inputLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 376, Short.MAX_VALUE))
                )
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(pane, javax.swing.GroupLayout.DEFAULT_SIZE, 150, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(inputLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(inputArea, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                )
        );
        
        
        
        frame.getContentPane().setLayout(layout);
        frame.setPreferredSize(new Dimension(400, 300));
		frame.setMinimumSize(new Dimension(300,250));

		// Display the window.
		frame.pack();
		frame.setVisible(true);
	}

	public static GuiConsole get() {
		if (instance == null) {
			instance = new GuiConsole();
			instance.show();
		}
		return instance;
	}

	public static void main(String[] args) {
		GuiConsole c = GuiConsole.get();
		while(true){
			while(!c.isShowing()){
				c.show();
			}
			try {
				c.println(c.readUntil("input?", '\n'));
			} catch (Exception e) {
			}
		}
		
//		for(int i=0; i<30; i++){
//			c.println("hello " + i);
//		}
//		System.out.println( c.readUntil("'~' to end", '~') );
//		System.out.println( c.readUntil("'\\n' to end", '\n') );
//		System.out.println( c.readUntil("'~' to end or length=5", '~', 5) );
//		System.out.println( c.readUntil("length=5 to end", 5) );
	}

}
