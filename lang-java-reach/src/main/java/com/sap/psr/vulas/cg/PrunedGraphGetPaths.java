package com.sap.psr.vulas.cg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.ibm.wala.util.graph.Graph;
import com.sap.psr.vulas.shared.json.model.ConstructId;

public class PrunedGraphGetPaths extends AbstractGetPaths {
	
	private static final Log log = LogFactory.getLog(PrunedGraphGetPaths.class);
	
	private long start_millis = System.currentTimeMillis(), end_millis = System.currentTimeMillis();	

	private Map<Integer, HashSet<Integer>> edges = new HashMap<Integer, HashSet<Integer>>();
	
	public PrunedGraphGetPaths(Graph<Integer> _graph, ArrayList<ConstructId> _nodeid) {
		super(_graph, _nodeid);
	}

	public HashSet<LinkedList<ConstructId>> getAllPaths(ConstructId _src, ConstructId _tgt) {
		this.start_millis = System.currentTimeMillis();
		int src_id = this.nodeId.indexOf(_src), tgt_id = this.nodeId.indexOf(_tgt);
		this.getAllEdges(tgt_id);
		this.end_millis = System.currentTimeMillis();
		PrunedGraphGetPaths.log.info("Finished computing all edges in [ " + (this.end_millis - this.start_millis) + " millisecs]");
		LinkedList<Integer> singlepath = new LinkedList<Integer>();
		HashSet<LinkedList<Integer>> allpaths = new HashSet<LinkedList<Integer>>();
		this.assemblePath(src_id, tgt_id, singlepath, allpaths);
		HashSet<LinkedList<ConstructId>> result = new HashSet<LinkedList<ConstructId>>();
		for(LinkedList<Integer> p : allpaths) {
			LinkedList<ConstructId> newp = new LinkedList<ConstructId>();
	    	for(Integer i : p) newp.add(this.nodeId.get(i));
	    	result.add(newp);
		}
		return result;
	}

	private void assemblePath(Integer _src, Integer _tgt, LinkedList<Integer> _path, HashSet<LinkedList<Integer>> _allpaths) {
		if (_src == _tgt) {
			LinkedList<Integer> newPath = new LinkedList<Integer>();
			newPath.addAll(_path);
			_allpaths.add(newPath);
			_path.removeLast();
			if((!_allpaths.isEmpty()) && _allpaths.size()%5 == 0) {
				this.end_millis = System.currentTimeMillis();
				PrunedGraphGetPaths.log.info("Found 5 more paths in [ " + (this.end_millis - this.start_millis) + " millisecs]");
			}
			return;
		} else {
			HashSet<Integer> edges = this.edges.get(_src);
			if (edges != null) {
				for (Integer i : edges) {
					if (!_path.contains(i)) {
						_path.add(i);
						assemblePath (i, _tgt, _path, _allpaths);
					}
				}
			}
		}
		if (_path != null && !_path.isEmpty())
			_path.removeLast();
	}
	
	/**
	 * Prune the callgraph, make it only contain all edges heading to the target
	 * (_tgt)
	 * 
	 * @param _tgt
	 * @return
	 */
	private void getAllEdges(Integer _tgt) {
		Iterator<Integer> nodes = this.graph.iterator();
		Integer node = null;
		// Intialization: no edge at all in the pruned callgraph
		while (nodes.hasNext()) {
			node = nodes.next();
			if (node == _tgt) {
				this.edges.put(node, new HashSet<Integer>());
			} else
				this.edges.put(node, null);
		}
		computeAllEdges(_tgt);
	}

	/**
	 * Recursive method of getAllEdges, update the Map _edges
	 * 
	 * @param _tgt
	 * @param _edges
	 * @return
	 */
	private void computeAllEdges(Integer _tgt) {
		Iterator<Integer> predNodes = this.graph.getPredNodes(_tgt);
		Integer prednode = null;
		while (predNodes.hasNext()) {
			prednode = predNodes.next();
			HashSet<Integer> newedge = this.edges.get(prednode);
			if (newedge == null) {
				newedge = new HashSet<Integer>();
			}
			if (!newedge.contains(_tgt)) {
				newedge.add(_tgt);
				this.edges.put(prednode, newedge);
				computeAllEdges(prednode);
			}
		}
	}	
}
