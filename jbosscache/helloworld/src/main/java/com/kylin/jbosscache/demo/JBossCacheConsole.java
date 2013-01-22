package com.kylin.jbosscache.demo;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;
import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.CacheStarted;
import org.jboss.cache.notifications.annotation.CacheStopped;
import org.jboss.cache.notifications.annotation.NodeCreated;
import org.jboss.cache.notifications.annotation.NodeModified;
import org.jboss.cache.notifications.annotation.ViewChanged;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.notifications.event.NodeEvent;
import org.jboss.cache.notifications.event.NodeModifiedEvent;
import org.jboss.cache.notifications.event.ViewChangedEvent;
import org.jgroups.Address;

import com.customized.tools.cli.TreeInputConsole;
import com.customized.tools.cli.TreeNode;

@CacheListener
public class JBossCacheConsole extends TreeInputConsole{
	
	private static final Logger log = Logger.getLogger(JBossCacheConsole.class);
	
	static final String CACHE_ADD = "add";
	static final String CACHE_MODIFY = "modify";
	static final String CACHE_UPDATE = "update";
	static final String CACHE_REMOVE = "remove";
	static final String CACHE_ADD_NODE = "add node";
	static final String CACHE_REMOVE_NODE = "remove node";
	
	private transient Cache<String, String> cache;
	private List<Address> membership = new LinkedList<Address>();
	private Address coordinator = null;
	
	private boolean debugCache ;
	
	private transient JBossCacheLogger cacheLogger;
	
	private transient Executor executor;
	
	public JBossCacheConsole(String name, TreeNode currentNode, JBossCacheModelDelegate cacheDelegate, boolean debugCache) {
		
		super(name, currentNode);
		
		this.cache = cacheDelegate.getGenericCache();
		this.debugCache = debugCache;
		
		executor = Executors.newCachedThreadPool();
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				cache.stop();
			}
		});

		cache.addCacheListener(this);
		
		cacheLogger = new JBossCacheLogger(cache, debugCache);
		
		init();
	}
	
	protected void handleADD(String pointer) {
		
		if(null == getCurrentNode()) {
			prompt("no CurrentNode exists");
			return;
		}
		
		String[] array = pointer.split(" ");
		
		if(array.length != 1) {
			promptErrorCommand(pointer);
			return ;
		}
		
		String strFqn = getAbsolutePath();
		prompt("Add/Modify JBossCache via Fully Qualified Name [" + strFqn + "]");
		String key = readString("Enter Key:", true);
		String value = readString("Enter Value:", true);
		
		Fqn<String> fqn = Fqn.fromString(strFqn);
		Node<String, String> node = cache.getNode(fqn);
		node.put(key, value);
		getCurrentNode().setContent(node.getData() + "");
	}

	protected void handleRM(String pointer) {
		
		if(null == getCurrentNode()) {
			prompt("no CurrentNode exists");
			return;
		}
		
		String[] array = pointer.split(" ");
		String key = "";
		if(array.length == 2) {
			key = array[1];
		} else {
			key = readString("Enter Key:", true);
		}
		
		String strFqn = getAbsolutePath();
		Fqn<String> fqn = Fqn.fromString(strFqn);
		Node<String, String> node = cache.getNode(fqn);
		
		if(key.equals("*") && isRemoving("all contents from JBossCache Node " + node.getFqn())) {
			node.clearData();
		} else if(isRemoving(key + " from JBossCache Node " + node.getFqn())) {
			node.remove(key);
		}
		
		getCurrentNode().setContent(node.getData() + "");
	}

	protected void handleOther(String pointer) {
		
		if(pointer.equals(CACHE_MODIFY) || pointer.equals(CACHE_UPDATE)) {
			handleADD(pointer);
		} else if(pointer.equals(CACHE_REMOVE)){
			handleRM(pointer);
		} else if(pointer.equals(CACHE_ADD_NODE)) {
			handleAddNode();
		} else if(pointer.equals(CACHE_REMOVE_NODE)) {
			handleRemoveNode();
		}
		
		super.handleOther(pointer);
	}

	private void handleRemoveNode() {
		
		Fqn<String> fqn = Fqn.fromString(getAbsolutePath());
		Node<String, String> node = cache.getNode(fqn);
		
		String fqnStr = readString("Enter JBossCache Fully Qualified Name:", true);
		
		if(isRemoving(" fqnStr From JBossCache " + fqn) && node.removeChild(Fqn.fromString(fqnStr))) {
			removeTreeNode(fqnStr);
		}
	}

	private void handleAddNode() {
		
		Node parentNode ;
		
		if(null == getCurrentNode()) {
			parentNode = cache.getRoot();
		} else {
			parentNode = cache.getNode(Fqn.fromString(getAbsolutePath()));
		}
		
		String fqn = readString("Enter JBossCache Fully Qualified Name:", true);
		Node node = parentNode.addChild(Fqn.fromString(fqn));
		prompt("Add JBossCache Node, Fully Qualified Name [" + node.getFqn() + "]");
		
		addTreeNode( new TreeNode(fqn, "", getCurrentNode(), null));
	}
	
	private synchronized void updateTreeNode(String path, Map<String, String> data) {
		
		String[] array = path.split("/");
		
		for(int i = 0 ; i < array.length ; i ++) {
//			node = 
		}
	}
	
	private void init() {
		
		List<Address> mbrship;

		mbrship = getMembers();
		if (mbrship != null && mbrship.size() > 0) {
			membership.clear();
			membership.addAll(mbrship);
			coordinator = mbrship.get(0);
		}
	}
	
	private List<Address> getMembers() {
		try {
			return new ArrayList<Address>(cache.getMembers());
		} catch (Throwable t) {
			log.error("JBossCacheConsole.getMembers(): ", t);
			return null;
		}
	}

	public String readString(String prompt, boolean validation) {
		// add Fqn validation
		return super.readString(prompt, validation);
	}
	
	@CacheStarted
	@CacheStopped
	public void cacheStartStopEvent(Event e) {
		cacheLogger.log(e);
	}
	
	@NodeCreated
	public void nodeCreated(NodeEvent e) {
		
		cacheLogger.log(e);
	}
	
	@NodeModified
	public void nodeModified(final NodeModifiedEvent e) {
		
		cacheLogger.log(e);
		
		Fqn fqn = e.getFqn();
		Map<String, String> data = e.getData();
		
	}
	
	@ViewChanged
	public void viewChange(final ViewChangedEvent e) {
		
		cacheLogger.log(e);
		
		Runnable r = new Runnable() {
			public void run() {
				List<Address> mbrship;
				if (e.getNewView() != null && (mbrship = e.getNewView().getMembers()) != null) {
					membership.clear();
					membership.addAll(mbrship);
					coordinator = mbrship.get(0);
				}
			}
		};
		
		executor.execute(r);
	}

}
