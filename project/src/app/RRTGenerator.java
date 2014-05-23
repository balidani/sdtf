package app;



import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.SwingUtilities;

import net.sf.appia.core.AppiaEventException;
import net.sf.appia.core.Direction;
import net.sf.appia.core.message.Message;
import tfsd.SampleApplSession;
import tfsd.SampleSendableEvent;

public class RRTGenerator {
	List<Point> points;
	
	int height;
	int width;
	Tree tree;

	private List<Integer> decisions;
	public int decision;

	public RRTGenerator(String fileName) {
		
		this.tree = new Tree();
		points = new ArrayList<Point>();
		
		decisions = new ArrayList<Integer>();
		decision = -1;

		// Read file, get the list of vertices
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(fileName));
			String line;
			line = br.readLine();
			String dimensions[] = line.trim().split(" ");
			width = Integer.parseInt(dimensions[0]);
			height = Integer.parseInt(dimensions[1]);

			while ((line = br.readLine()) != null) {
				// process the line.
				String tokens[] = line.trim().split(" ");
				points.add(new Point(Double.parseDouble(tokens[0]), Double
						.parseDouble(tokens[1])));
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void printPoints() {
		System.out.println("width : " + width + " , height : " + height);
		for (Point t : points) {
			System.out.println(t.toString());
		}

	}

	public List<Point> getPoints() {
		return points;
	}


	public synchronized void generate() {

		while (points.size() > 0) {

			// Take a point
			Point selection = new Point(width / 2, height / 2);
			Point nearest = findNearest(selection, points);

			tree.vertices.add(nearest);
			tree.edges.add(new Edge(nearest, nearest));

			points.remove(nearest);

			// Generate a random number for proposal
			Random rand = new Random();
			int dex = rand.nextInt(points.size());

			// Send the proposal

			try {

				SampleSendableEvent event = new SampleSendableEvent();
				event.setCommand("propose");
				Message message = event.getMessage();
				message.pushString(Integer.toString(dex));
				event.asyncGo(SampleApplSession.rbChannel, Direction.DOWN);
			} catch (AppiaEventException e1) {
				e1.printStackTrace();
			}

			try {
				this.wait();
			} catch (InterruptedException e) {}
			
			System.out.println("[RRT] Got decision " + decision);
			decisions.add(decision);
			
			selection = points.get(decision);
			connect(selection, tree.edges);

			tree.vertices.add(selection);
			points.remove(decision);
			
			SwingUtilities.invokeLater(new Runnable() {
	            @Override
	            public void run() {
	            	RRTHandler.drawer.repaint();
	                RRTHandler.drawer.setVisible(true);
	            }
	        });

			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}

		finished();
	}

	private void finished() {
		System.out.println("Finished");
		// tree.printTree();
		for (int i : decisions) {
			System.out.print(i + " ");
		}
		System.out.println();
	}

	public Point findNearest(Point t, List<Point> points) {
		Point nearest = null;
		double distance = Double.MAX_VALUE;
		double current;
		for (Point p : points) {
			current = getDistance(t, p);
			if (current <= distance) {
				nearest = p;
				distance = current;
			}
		}
		return nearest;
	}

	public double getDistance(Point s, Point t) {
		double result = Math.sqrt(Math.pow(s.getX() - t.getX(), 2)
				+ Math.pow(s.getY() - t.getY(), 2));
		// System.out.println(result);
		return result;
	}

	public void connect(Point s, List<Edge> edges) {
		Point nearest = null;
		/*
		 * distance( Point P, Segment P0:P1 ) { v = P1 - P0 w = P - P0
		 * 
		 * if ( (c1 = w·v) <= 0 ) // before P0 return d(P, P0) if ( (c2 = v·v)
		 * <= c1 ) // after P1 return d(P, P1)
		 * 
		 * b = c1 / c2 Pb = P0 + bv return d(P, Pb) }
		 */

		double distance = Double.MAX_VALUE;
		Edge nearestEdge = null;
		int status = 0;
		for (Edge edge : edges) {
			Point begin = edge.getBegin();
			Point end = edge.getEnd();

			Point v = new Point(end.x - begin.x, end.y - begin.y);
			Point w = new Point(s.x - begin.x, s.y - begin.y);

			double c1 = w.x * v.x + w.y * v.y;
			double c2 = v.x * v.x + v.y * v.y;
			if (c1 <= 0) {
				if (getDistance(s, begin) <= distance) {
					nearest = begin;
					nearestEdge = edge;
					distance = getDistance(s, begin);
					status = -1;
				}
				continue;
			}
			if (c2 <= c1) {
				if (getDistance(s, end) <= distance) {
					nearest = end;
					nearestEdge = edge;
					distance = getDistance(s, end);
					status = 1;
				}
				continue;
			}

			double b = c1 / c2;
			Point Pb = new Point(begin.x + b * v.x, begin.y + b * v.y);
			if (getDistance(s, Pb) <= distance) {
				nearest = Pb;
				nearestEdge = edge;
				distance = getDistance(s, Pb);
				status = 0;
			}
		}
		switch (status) {
		case 0:
			edges.add(new Edge(nearestEdge.getBegin(), nearest));
			edges.add(new Edge(nearestEdge.getEnd(), nearest));
			edges.add(new Edge(s, nearest));
			edges.remove(nearestEdge);
			break;
		case 1:
		case -1:
			edges.add(new Edge(s, nearest));
			break;
		}

	}
}
