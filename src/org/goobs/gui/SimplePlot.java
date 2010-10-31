package org.goobs.gui;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JFrame;
import info.monitorenter.gui.chart.Chart2D;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.traces.Trace2DSimple;

public class SimplePlot {

	private Lock plotLock = new ReentrantLock();
	private Condition shouldContinue = plotLock.newCondition();
	
	private Chart2D chart;
	private JFrame frame = null;
	private int maxPoints = Integer.MAX_VALUE;
	
	@SuppressWarnings("unused")
	private String title;
	@SuppressWarnings("unused")
	private String domain = "X Axis";
	@SuppressWarnings("unused")
	private String range = "Y Axis";
	
	public SimplePlot(String title) {
		super();
		this.title = title;
		this.chart = new Chart2D();
	}
	
	public SimplePlot setAxis(String domain, String range){
		this.domain = domain;
		this.range = range;
		return this;
	}
	
	public SimplePlot setMaxPoints(int max){
		this.maxPoints = max;
		return this;
	}
	
	public SimplePlot plot(double[] x, double[] y){
		return this.plot("Untitled", x, y, Color.BLACK);
	}
	
	public SimplePlot plot(String name, double[] x, double[] y, Color color){
		plotLock.lock();
		// Create an ITrace:
		ITrace2D trace = new Trace2DSimple(name);
		trace.setColor(color);
		// Add all points, as it is static:
		if(x.length != y.length) throw new IllegalArgumentException("X and Y data have different lengths: x=" + x.length + ", y=" + y.length);
		for(int i=0; i<Math.min(x.length,this.maxPoints); i++){
			trace.addPoint(x[i], y[i]);
		}
		// Add the trace to the chart:
		chart.addTrace(trace);
		// Make it visible:
		if(frame == null){
			// Create a frame.
			frame = new JFrame("MinimalStaticChart");
			// add the chart to the frame:
			frame.getContentPane().add(chart);
			frame.setSize(1000, 1000);
			// Enable the termination button [cross on the upper right edge]:
			frame.addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					plotLock.lock();
					frame = null;
					shouldContinue.signalAll();
					plotLock.unlock();
				}
			});
		}
		frame.setVisible(true);
		plotLock.unlock();
		return this;
	}
	
	public void waitForClose(){
		plotLock.lock();
		while(frame != null){
			shouldContinue.awaitUninterruptibly();
		}
		plotLock.unlock();
	}
}