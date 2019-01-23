package pers.zyc.zkgui;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZKUtil;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.zyc.tools.utils.Regex;
import pers.zyc.tools.zkclient.ZKClient;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author zhangyancheng
 */
public class Main extends Application implements Bridge, InvocationHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	private static final String CONNECT_PAGE = "/page/connect.html";
	private static final String NODE_PAGE = "/page/node.html";
	private static final ObjectMapper OM = new ObjectMapper();

	private final String HISTORY_FILE = System.getProperty("user.home") + "/.zkgui_history";
	private final List<String> HISTORY_SET = new CopyOnWriteArrayList<>();
	private Stage stage;
	private ZKClient zkClient;
	private WebEngine webEngine;
	private Bridge javaMember;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void init() throws Exception {
		CompletableFuture.runAsync(() -> {
			try {
				File file = new File(HISTORY_FILE);
				if ((!file.exists() && !file.createNewFile()) || !file.canRead() || !file.canWrite()) {
					return;
				}
				try (BufferedReader br = new BufferedReader(new FileReader(file))) {
					HISTORY_SET.addAll(br.lines().limit(8).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
					LOGGER.info("Loading histories: {}", HISTORY_SET);
				}
				long last = 0;
				while (!Thread.currentThread().isInterrupted()) {
					long hashcode = HISTORY_SET.hashCode();
					if (hashcode != last) {
						last = hashcode;
						try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
							for (String his : HISTORY_SET) {
								bw.write(his);
								bw.newLine();
							}
							bw.flush();
						}
					}
					TimeUnit.SECONDS.sleep(5);
				}
			} catch (Exception e) {
				LOGGER.error("Processing history error", e);
			}
		});
		javaMember = (Bridge) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Bridge.class}, this);
	}

	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;
		WebView webview = new WebView();
		stage.setScene(new Scene(webview, 1000, 600));
		stage.getIcons().add(new Image(getClass().getResourceAsStream("/logo.png")));
		stage.setOnCloseRequest(v -> {
			Optional.ofNullable(zkClient).ifPresent(ZKClient::destroy);
			System.exit(0);
		});
		webEngine = webview.getEngine();
		webEngine.load(getClass().getResource(CONNECT_PAGE).toString());
		stage.show();
		webEngine.getLoadWorker()
				.stateProperty()
				.addListener((obs, oldValue, newValue) -> {
					if (newValue == Worker.State.SUCCEEDED) {
						String pageLocation = webEngine.getLocation();
						LOGGER.info("Loading {} success", pageLocation);
						JSObject jsWindow = (JSObject) webEngine.executeScript("window");
						jsWindow.setMember("javaMember", javaMember);
						switch (pageLocation.substring(pageLocation.lastIndexOf("/") + 1)) {
							case "connect.html":
								String allHistoryStr = HISTORY_SET.stream().collect(Collectors.joining("|"));
								jsWindow.call("typeHead", allHistoryStr);
								break;
							case "node.html":
								jsWindow.call("getNodeInfo", "/");
								break;
							default:
								throw new Error();
						}
					}
				});
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		String returnToJs;
		try {
			returnToJs = OM.writeValueAsString(method.invoke(this, args));
		} catch (Throwable e) {
			if (e instanceof InvocationTargetException) {
				e = ((InvocationTargetException) e).getTargetException();
			}
			LOGGER.warn("Invoke error: {}", e.getMessage());
			returnToJs = "{\"error\": \"" + e.getMessage() + "\"}";
		}
		LOGGER.debug("Return to js: {}", returnToJs);
		return returnToJs;
	}

	@Override
	public Object connect(String connectString) throws Exception {
		if (isBlank(connectString)) {
			throw new RuntimeException("连接字符串不能为空!");
		} else if (!Regex.ZK_ADDRESS.matches(connectString)) {
			throw new RuntimeException("连接字符串格式错误!");
		} else {
			zkClient = new ZKClient(connectString, 30000);
			if (!zkClient.waitToConnected(3, TimeUnit.SECONDS)) {
				zkClient.destroy();
				throw new RuntimeException("连接超时(3sec)!");
			}
			HISTORY_SET.remove(connectString);
			HISTORY_SET.add(0, connectString);
			webEngine.load(getClass().getResource(NODE_PAGE).toString());
			return "{\"success\": true}";
		}
	}

	@Override
	public Object getNodeInfo(String node) throws Exception {
		return getNodeInfoFromZk(node);
	}

	@Override
	public void quit() {
		zkClient.destroy();
		webEngine.getHistory().go(-1);
		stage.setTitle(null);
	}

	private static boolean isBlank(String cs) {
		int strLen;
		if (cs == null || (strLen = cs.length()) == 0) {
			return true;
		}
		for (int i = 0; i < strLen; i++) {
			if (!Character.isWhitespace(cs.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private Map<String, Object> getNodeInfoFromZk(String node) throws Exception {
		List<String> children = zkClient.getChildren(node);
		Stat stat = new Stat();
		byte[] data = zkClient.getData(node, stat);
		Map<String, Object> model = new HashMap<>();
		model.put("children", children);
		model.put("data", data == null ? null : new String(data, StandardCharsets.UTF_8));
		model.put("stat", stat);
		stage.setTitle(node);
		return model;
	}

	@Override
	public Object createNode(String node, String data, boolean ephemeral, boolean sequential) throws Exception {
		zkClient.create(node, data == null ? null : data.getBytes(StandardCharsets.UTF_8),
				CreateMode.fromFlag((ephemeral ? 1 : 0) + (sequential ? 2 : 0)));
		return getNodeInfoFromZk(getParent(node));
	}

	@Override
	public Object setData(String node, String data) throws Exception {
		zkClient.setData(node, data == null ? null : data.getBytes(StandardCharsets.UTF_8));
		return getNodeInfoFromZk(node);
	}

	@Override
	public Object deleteNode(String node) throws Exception {
		ZKUtil.deleteRecursive(zkClient.getZooKeeper(), node);
		return getNodeInfoFromZk(getParent(node));
	}

	private static String getParent(String node) {
		String parent = node.substring(0, node.lastIndexOf("/"));
		if (isBlank(parent)) {
			parent = "/";
		}
		return parent;
	}
}