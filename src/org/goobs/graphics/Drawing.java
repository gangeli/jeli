package org.goobs.graphics;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class Drawing {

  //---------------
  // INNER CLASSES
  //---------------
  private static final class DrawingComponent extends JComponent {
    public final Drawing toDraw;
    private int width;
    private int height;
    public DrawingComponent(Drawing toDraw, int width, int height){
      this.toDraw = toDraw;
      this.width = width;
      this.height = height;
    }
    public void update(int width, int height){
      this.width = width;
      this.height = height;
    }
    @Override
    public void paint(Graphics g){
      super.paint(g);
      Graphics2D graphics = (Graphics2D) g;
      //(draw at offset (0,0))
      graphics.drawImage(
          toDraw.render(width,height),
          0,
          0,
          this);
    }
  }

  private static final class Viewer extends JFrame{
    public final Drawing drawing;
    public final int width;
    public final int height;
    private boolean havePainted = false;
    private Viewer(Drawing drawing, int width, int height){
      //(super constructor)
      super();
      //(variables)
      this.drawing = drawing;
      this.width = width;
      this.height = height;
      //(configure window)
      this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      this.setTitle("Drawing");
    }
    @Override
    public void paint(Graphics g){
      //--Modify
      Insets insets = this.getInsets();
      if(!havePainted){
        //(case: first paint)
        this.setContentPane(new DrawingComponent(drawing, width, height));
        this.setBounds(100,100,
            width+insets.left+insets.right,
            height+insets.top+insets.bottom);
        havePainted = true;
      } else {
        //(set bounds)
        ((DrawingComponent) this.getContentPane()).update(
            this.getWidth()-insets.left-insets.right,
            this.getHeight()-insets.top-insets.bottom
          );
      }
      //--Call Paint
      super.paint(g);
    }

  }

  private static interface Action {
    public void perform(BufferedImage img, double minX, double maxX, double minY, double maxY);
  }

  //---------------
  // SETUP
  //---------------

  private double minX = Integer.MAX_VALUE;
  private double maxX = Integer.MIN_VALUE;
  private double minY = Integer.MAX_VALUE;
  private double maxY = Integer.MIN_VALUE;

  private java.util.List<Action> actions = new ArrayList<Action>();

  public Drawing(){

  }

  //---------------
  // PRIVATE DRAW METHODS
  //---------------

  private Drawing append(Action act){
    this.actions.add(act);
    return this;
  }

  private void updateBounds(double x0, double x1, double y0, double y1){
    minX = Math.min(minX, x0);
    minX = Math.min(minX, x1);
    maxX = Math.max(maxX, x0);
    maxX = Math.max(maxX, x1);
    minY = Math.min(minY, y0);
    minY = Math.min(minY, y1);
    maxY = Math.max(maxY, y0);
    maxY = Math.max(maxY, y1);
  }

  private Action doLine(final double x0, final double y0, final double x1, final  double y1, final Color color, final Stroke stroke){
    //(update bounds)
    updateBounds(x0, x1, y0, y1);
    //(create action)
    return new Action(){
      @Override
      public void perform(BufferedImage img, double minX, double maxX, double minY, double maxY) {
        //(set up graphics)
        Graphics2D graphics = img.createGraphics();
        graphics.setColor(color);
        graphics.setStroke(stroke);
        //(draw)
        graphics.drawLine(
            translateX(x0, minX, maxX, img.getWidth()),
            translateY(y0, minY, maxY, img.getHeight()),
            translateX(x1, minX, maxX, img.getWidth()),
            translateY(y1, minY, maxY, img.getHeight())
        );
      }
    };
  }

   private Action doRect(final double x0, final double y0, final double x1, final  double y1, final Color lineColor, final Stroke stroke, final Color fillColor){
     //(update bounds)
     updateBounds(x0, x1, y0, y1);
     //(create action)
     return new Action(){
       @Override
       public void perform(BufferedImage img, double minX, double maxX, double minY, double maxY) {
         //(set up graphics)
         Graphics2D graphics = img.createGraphics();
         //(fill)
         if(fillColor != null){
           graphics.setColor(fillColor);
           graphics.fillRect(
               translateX(Math.min(x0, x1), minX, maxX, img.getWidth()),
               translateY(Math.max(y0, y1), minY, maxY, img.getHeight()),
               translateDX(Math.abs(x0 - x1), minX, maxX, img.getWidth()),
               translateDY(Math.abs(y0 - y1), minY, maxY, img.getHeight())
           );
         }
         //(draw)
         if(lineColor != null){
           graphics.setColor(lineColor);
           graphics.setStroke(stroke);
           graphics.drawRect(
               translateX(Math.min(x0,x1), minX, maxX, img.getWidth()),
               translateY(Math.max(y0,y1), minY, maxY, img.getHeight()),
               translateDX(Math.abs(x0-x1), minX, maxX, img.getWidth()),
               translateDY(Math.abs(y0-y1), minY, maxY, img.getHeight())
           );
         }
       }
     };
   }

  private Action doOval(final double x0, final double y0, final double rX, final double rY, final Color lineColor, final Color fillColor, final Stroke stroke){
    //(update bounds)
    updateBounds(x0-rX, x0+rX, y0-rY, y0+rY);
    //(create action)
    return new Action(){
      @Override
      public void perform(BufferedImage img, double minX, double maxX, double minY, double maxY) {
        //(set up graphics)
        Graphics2D graphics = img.createGraphics();
        //(fill)
        if(fillColor != null){
          graphics.setColor(fillColor);
          graphics.fillOval(
            translateX(x0-rX,minX,maxX,img.getWidth()),
            translateY(y0+rY,minY,maxY,img.getHeight()),
            translateDX(rX * 2, minX, maxX, img.getWidth()),
            translateDY(rY * 2, minY, maxY, img.getHeight())
          );
        }
        //(draw)
        if(lineColor != null){
          graphics.setColor(lineColor);
          graphics.setStroke(stroke);
          graphics.drawOval(
              translateX(x0-rX,minX,maxX,img.getWidth()),
              translateY(y0+rY,minY,maxY,img.getHeight()),
              translateDX(rX * 2, minX, maxX, img.getWidth()),
              translateDY(rY * 2, minY, maxY, img.getHeight())
          );
        }
      }
    };
  }

  private Action doText(final double x0, final double y0, final String text, final Color color, final Font fontCand,final double size){
    //(create action)
    return new Action(){
      @Override
      public void perform(BufferedImage img, double minX, double maxX, double minY, double maxY) {
        //(set up graphics)
        Graphics2D graphics = img.createGraphics();
        graphics.setColor(color);
        Font font = new Font(fontCand.getName(), fontCand.getStyle(), translateDY(size,minY,maxY,img.getHeight()));
        graphics.setFont(font);
        //(draw)
        graphics.drawChars(
            text.toCharArray(),
            0,
            text.length(),
            translateX(x0,minX,maxX,img.getWidth()),
            translateY(y0,minY,maxY,img.getHeight())
          );
      }
    };
  }

  //---------------
  // DRAW METHODS
  //---------------

  public Drawing line(double x0, double y0, double x1, double y1, Color color, int width){
    return append(doLine(x0, y0, x1, y1, color, new BasicStroke(width)));
  }
  public Drawing line(double x0, double y0, double x1, double y1, Color color){ return line(x0,y0,x1,y1,color,1); }
  public Drawing line(double x0, double y0, double x1, double y1){ return line(x0,y0,x1,y1,Color.BLACK); }

  public Drawing rect(double x0, double y0, double x1, double y1, Color color, int width, Color fill){
    return append(doRect(x0, y0, x1, y1, color, new BasicStroke(width), fill));
  }
  public Drawing rect(double x0, double y0, double x1, double y1, Color color, int weight){ return rect(x0, y0, x1, y1, color, weight, null); }
  public Drawing rect(double x0, double y0, double x1, double y1, Color color){ return rect(x0, y0, x1, y1, color,1); }
  public Drawing rect(double x0, double y0, double x1, double y1){ return rect(x0, y0, x1, y1, Color.BLACK); }
  public Drawing fillRect(double x0, double y0, double x1, double y1, Color fill){ return rect(x0,y0,x1,y1,null,0,fill); }

  public Drawing circle(double x, double y, double r, Color color, int width, Color fill){
    return append(doOval(x,y,r,r,color,fill,new BasicStroke(width)));
  }
  public Drawing circle(double x, double y, double r, Color color, int width){ return circle(x,y,r,color,width,null); }
  public Drawing circle(double x, double y, double r, Color color){ return circle(x,y,r,color,1); }
  public Drawing circle(double x, double y, double r){ return circle(x,y,r,Color.BLACK); }
  public Drawing fillCircle(double x, double y, double r, Color fill){ return circle(x,y,r,null,0,fill); }

  public Drawing text(double x, double y, String text, Color color, Font font, double size){
    return append(doText(x,y,text,color,font,size));
  }
  public Drawing text(double x, double y, String text, Color color, Font font){ return text(x,y,text,color,font,font.getSize()); }
  public Drawing text(double x, double y, String text, Color color, double size){ return text(x,y,text,color,new Font(Font.MONOSPACED, Font.PLAIN, (int) size), size); }
  public Drawing text(double x, double y, String text, Color color){ return text(x,y,text,color,12); }
  public Drawing text(double x, double y, String text){ return text(x,y,text,Color.BLACK); }
  public Drawing text(double x, double y, String text, double size){ return text(x,y,text,Color.BLACK,size); }

  //---------------
  // USE METHODS
  //---------------

  public void preview(int width, int height){
    new Viewer(this, width, height).setVisible(true);
  }
  public void preview(int largestDimension){
    //--Variables
    int width;
    int height;
    //--Calculate Width/Height
    if(maxX-minX > maxY-minY){
      //(case: width greater)
      width = largestDimension;
      height = (int) ((maxY-minY) * ((double) largestDimension) / (maxX-minX));
    } else {
      //(case: height greater)
      height = largestDimension;
      width = (int) ((maxX-minX) * ((double) largestDimension) / (maxY-minY));
    }
    //--Preview
    preview(width,height);
  }
  public void preview(){ preview((int) (maxX-minX), (int) (maxY-minY)); }

  public Image render(int width, int height){
    //(error check)
    if(this.actions.isEmpty()){
      throw new IllegalStateException("Cannot render a blank image");
    }
    //(variables)
    BufferedImage image = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
    //(initialize to white)
    int[] rgb = new int[width*height];
    Arrays.fill(rgb, 0xFFFFFF);
    image.setRGB(0,0,width,height,rgb, 0, width);
    //(perform actions)
    for(Action act : actions){
      act.perform(image,minX,maxX,minY,maxY);
    }
    //(return)
    return image;
  }


  //---------------
  // PRIVATE UTILITIES
  //---------------

  private static int translateX(double x, double minX, double maxX, int width){
    double ratio = ((double) width) / (maxX-minX);
    return (int) ((x - minX) * ratio);
  }

  private static int translateY(double y, double minY, double maxY, int height){
    double ratio = ((double) height) / (maxY-minY);
    int translated = (int) ((y - minY) * ratio);
    return height - translated;
  }

  private static int translateDX(double v, double minX, double maxX, int width) {
    double ratio = ((double) width) / (maxX-minX);
    return (int) (v*ratio);
  }
  private static int translateDY(double v, double minY, double maxY, int height){
    double ratio = ((double) height) / (maxY-minY);
    return (int) (v*ratio);
  }


  public static void main(String[] args){
    Drawing d = new Drawing()
        .line(-100,-100,300,100)
        .line(-100,100,100,-100)
        .circle(0,0,50)
        .fillCircle(50,50,20,Color.RED)
        .line(0,0,30,60)
        .rect(0, 0, 30, 60)
        .fillRect(0, 0, 30, 30, Color.BLUE)
        .circle(0,-50,25,Color.BLUE,3,Color.GRAY)
        .text(50,0,"Hello World!", Color.GREEN, 20);
    d.preview(500);
  }

}
