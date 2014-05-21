package app;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.SwingUtilities;

public class RRTGenerator {
	List<Point> points;
	int height;
	int width;
	Tree tree;

	public RRTGenerator(String fileName) {
		this.tree = new Tree();
		points = new ArrayList<Point>();

		// TODO read file, get the list of vertices
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
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

	public void generate() {
		Tree rrt = this.tree;

		Point selection = new Point(width / 2, height / 2);
		Point nearest = findNearest(selection, points);
		rrt.vertices.add(nearest);
		rrt.edges.add(new Edge(nearest, nearest));
		points.remove(nearest);

		Random rand = new Random();
		while (points.size() != 0) {
			int dex = rand.nextInt(points.size());
			selection = points.get(dex);
			connect(selection, rrt.edges);
			// nearest = findNearest(selection, rrt.vertices);
			// rrt.edges.add(new Edge(nearest,selection));
			rrt.vertices.add(selection);
			points.remove(dex);
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					Application.drawer.repaint();
					Application.drawer.setVisible(true);
				}
			});
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
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
		boolean inside = false;
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
