package app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

class Surface extends JPanel {
	Tree tree;
	public Surface(Tree tree){
		this.tree = tree;
	}
	
    private void doDrawing(Graphics g) {

        Graphics2D g2d = (Graphics2D) g;

        g2d.setColor(Color.blue);

        Dimension size = getSize();
        Insets insets = getInsets();

        int w = size.width - insets.left - insets.right;
        int h = size.height - insets.top - insets.bottom;

        Random r = new Random();

        for(Point p : tree.vertices) {
        	g2d.drawLine((int)p.getX(), (int)p.getY(),(int) p.getX(),(int) p.getY());
        }
        g2d.setColor(Color.red);
        for(Edge e : tree.edges) {
        	g2d.drawLine((int)e.getBegin().getX(),(int) e.getBegin().getY(),(int) e.getEnd().getX(),(int) e.getEnd().getY());
        }
        
    }
    @Override
    public void paintComponent(Graphics g) {

        super.paintComponent(g);
        doDrawing(g);
    }
}

public class Drawer extends JFrame {

    public Drawer(Tree tree) {
        initUI(tree);
    }

    private void initUI(Tree tree) {
        
        setTitle("Points");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        add(new Surface(tree));

        setSize(800,800);
        setLocationRelativeTo(null);
    }
}