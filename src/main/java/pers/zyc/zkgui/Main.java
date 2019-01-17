package pers.zyc.zkgui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.ba;
import com.teamdev.jxbrowser.chromium.swing.BrowserView;
import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZKUtil;
import org.apache.zookeeper.data.Stat;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.ErrorMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.MultipartAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;
import pers.zyc.tools.utils.Regex;
import pers.zyc.tools.zkclient.ZKClient;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author zhangyancheng
 */
@Controller
@SpringBootApplication(exclude = {ErrorMvcAutoConfiguration.class, MultipartAutoConfiguration.class})
public class Main {

	private static final int WEB_SERVER_PORT = 8090;
	private static final String HISTORY_FILE = System.getProperty("user.home") + "/.zkgui_history";
	private static final List<String> HISTORY_SET = new CopyOnWriteArrayList<>();
	private static JFrame frame;
	private static ZKClient zkClient;

	public static void main(String[] args) throws Exception {
		authIfNeed();
		loadHistory();
		SpringApplication app = new SpringApplication(Main.class);
		app.setHeadless(false);
		app.setBannerMode(Banner.Mode.OFF);
		app.setDefaultProperties(new HashMap<String, Object>() {
			{
				put("server.port", WEB_SERVER_PORT);
				put("server.tomcat.max-threads", 2);
				put("server.session.timeout", -1);
				put("spring.thymeleaf.mode", "HTML5");
				put("spring.thymeleaf.encoding", "UTF-8");
				put("spring.thymeleaf.content-type", "text/html");
				put("spring.thymeleaf.cache", false);
				put("spring.thymeleaf.prefix", "classpath:/templates/");
				put("spring.thymeleaf.suffix", ".htm");
				put("spring.resources.static-locations", "classpath:/static");
				put("spring.mvc.static-path-pattern", "/static/**");
				put("spring.mvc.throw-exception-if-no-handler-found", true);

				put("spring.jmx.enabled", false);
				put("spring.mvc.formcontent.putfilter.enabled", false);
			}
		});
		app.addListeners((EmbeddedServletContainerInitializedEvent e) -> {
			Toolkit toolkit = Toolkit.getDefaultToolkit();
			Dimension screenSize = toolkit.getScreenSize();
			frame = new JFrame();
			frame.setSize((int) (screenSize.width * 0.625), (int) (screenSize.height * 0.75));
			frame.setLocationRelativeTo(null);
			frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			frame.setIconImage(toolkit.createImage(Main.class.getResource("/static/logo.png")));

			Browser browser = new Browser();
			frame.add(new BrowserView(browser), BorderLayout.CENTER);
			frame.setVisible(true);
			browser.loadURL("http://localhost:" + WEB_SERVER_PORT);
		});
		app.addListeners((ContextClosedEvent e) -> Optional.ofNullable(zkClient).ifPresent(ZKClient::destroy));
		app.run(args);
	}

	private static void loadHistory() {
		new Thread(() -> {
			try {
				File file = new File(HISTORY_FILE);
				if ((!file.exists() && !file.createNewFile()) || !file.canRead() || !file.canWrite()) {
					return;
				}
				try (BufferedReader br = new BufferedReader(new FileReader(file))) {
					HISTORY_SET.addAll(br.lines().limit(8).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
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
					TimeUnit.SECONDS.sleep(1);
				}
			} catch (Exception ignored) {
			}
		}) {{setDaemon(true);}}.start();
	}

	private static void authIfNeed() throws Exception {
		ClassPathResource cpr = new ClassPathResource("/META-INF/teamdev.licenses");
		if (!cpr.exists()) {
			throw new FileNotFoundException("Missing teamdev.licenses");
		}
		try (BufferedReader br = new BufferedReader(new InputStreamReader(cpr.getInputStream()))) {
			if (br.lines().noneMatch("SigB: 1"::equals)) {
				return;
			}
			Field mf = Field.class.getDeclaredField("modifiers");
			mf.setAccessible(true);
			Stream.of("e", "f").forEach(s -> {
				try {
					Field f = ba.class.getDeclaredField(s);
					f.setAccessible(true);
					mf.setInt(f, f.getModifiers() & ~Modifier.FINAL);
					f.set(null, new BigInteger("1"));
				} catch (Exception ignored) {
				}
			});
			mf.setAccessible(false);
		}
	}

	@RequestMapping(path = "/", method = RequestMethod.GET)
	public String connect(ModelMap modelMap) {
		modelMap.put("history", HISTORY_SET);
		frame.setTitle("连接到ZooKeeper");
		return "connect";
	}

	@RequestMapping(path = "/", method = RequestMethod.POST)
	public void connect(HttpSession session, HttpServletResponse response, String connectString) throws Exception {
		session.removeAttribute("connectErr");
		session.setAttribute("connectString", connectString);

		if (!Regex.ZK_ADDRESS.matches(connectString)) {
			session.setAttribute("connectErr", "错误的连接字符串!");
			response.sendRedirect("/");
			return;
		}
		ZKClient zkClient = new ZKClient(connectString, 30000);
		if (!zkClient.waitToConnected(3, TimeUnit.SECONDS)) {
			zkClient.destroy();
			session.setAttribute("connectErr", "连接超时!");
			response.sendRedirect("/");
			return;
		}
		Main.zkClient = zkClient;
		response.sendRedirect("/node");
		HISTORY_SET.removeIf(connectString::equals);
		HISTORY_SET.add(0, connectString);
	}

	@RequestMapping("/quit")
	public void quit(HttpServletResponse response) throws IOException {
		zkClient.destroy();
		frame.setTitle(null);
		response.sendRedirect("/");
	}

	@RequestMapping(path = "/info/**")
	@ResponseBody
	public Object nodeInfo(HttpServletRequest request) throws Exception {
		String node = request.getRequestURI().substring(5);
		if (StringUtils.isBlank(node)) {
			node = "/";
		}
		return getNodeInfo(node);
	}

	private static Map<String, Object> getNodeInfo(String node) throws Exception {
		List<String> children = zkClient.getChildren(node);
		Stat stat = new Stat();
		byte[] data = zkClient.getData(node, stat);
		Map<String, Object> model = new HashMap<>();
		model.put("children", children);
		model.put("data", data == null ? null : new String(data, StandardCharsets.UTF_8));
		model.put("stat", stat);
		frame.setTitle(node);
		return model;
	}

	@RequestMapping(path = "/create", method = RequestMethod.POST)
	@ResponseBody
	public Object createNode(String node, String data, boolean ephemeral, boolean sequential) throws Exception {
		zkClient.create(node, data == null ? null : data.getBytes(StandardCharsets.UTF_8),
				CreateMode.fromFlag((ephemeral ? 1 : 0) + (sequential ? 2 : 0)));
		return getNodeInfo(getParent(node));
	}

	@RequestMapping(path = "/setData", method = RequestMethod.POST)
	@ResponseBody
	public Object setData(String node, String data) throws Exception {
		zkClient.setData(node, data == null ? null : data.getBytes(StandardCharsets.UTF_8));
		return getNodeInfo(node);
	}

	@RequestMapping(path = "/delete", method = RequestMethod.POST)
	@ResponseBody
	public Object deleteNode(String node) throws Exception {
		ZKUtil.deleteRecursive(zkClient.getZooKeeper(), node);
		return getNodeInfo(getParent(node));
	}

	private static String getParent(String node) {
		String parent = node.substring(0, node.lastIndexOf("/"));
		if (StringUtils.isBlank(parent)) {
			parent = "/";
		}
		return parent;
	}

	@Configuration
	@SuppressWarnings("unused")
	static class MvcConfigurer extends WebMvcConfigurerAdapter {

		@Override
		public void addViewControllers(ViewControllerRegistry registry) {
			registry.addViewController("/node").setViewName("node");
		}

		@Override
		public void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {
			ObjectMapper om = new ObjectMapper();
			SimpleMappingExceptionResolver exceptionResolver = new SimpleMappingExceptionResolver() {
				@Override
				protected void logException(Exception ex, HttpServletRequest request) {
					logger.error("System error", ex);
				}

				@Override
				protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response,
														  Object handler, Exception ex) {
					response.setContentType("application/json;charset=UTF-8");
					try (PrintWriter responseWriter = response.getWriter()) {
						om.writeValue(responseWriter, new HashMap<String, Object>(2) {
							{
								put("code", 0);
								put("error", ex.getMessage());
							}
						});
					} catch (IOException e) {
						logException(ex, request);
					}
					return new ModelAndView();
				}
			};
			exceptionResolver.setDefaultStatusCode(500);
			exceptionResolver.setDefaultErrorView("error");
			exceptionResolvers.add(exceptionResolver);
		}
	}
}