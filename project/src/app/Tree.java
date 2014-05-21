package app;

import java.util.ArrayList;
import java.util.List;


public class Tree {
	public List<Point> vertices;
	public List<Edge> edges;
	public Tree() {
		this.vertices = new ArrayList<Point>();
		this.edges = new ArrayList<Edge>();
	}
	

	public void printTree() {
		for(Edge edge : edges) {
			System.out.println(edge.getBegin().toString()+"-->"+edge.getEnd().toString());
		}
	}
	
	
}
