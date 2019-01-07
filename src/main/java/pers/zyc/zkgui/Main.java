package pers.zyc.zkgui;

import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.ba;
import com.teamdev.jxbrowser.chromium.swing.BrowserView;
import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.data.Stat;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.ErrorMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.MultipartAutoConfiguration;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author zhangyancheng
 */
@Controller
@SpringBootApplication(exclude = {
		ErrorMvcAutoConfiguration.class,
		MultipartAutoConfiguration.class
})
public class Main extends JFrame implements ApplicationListener<EmbeddedServletContainerInitializedEvent> {

	private static final int WEB_SERVER_PORT = 8090;
	private static final String HISTORY_FILE = System.getProperty("user.home") + "/.zkgui_history";
	private static final List<String> HISTORY_SET = new CopyOnWriteArrayList<>();

	public static void main(String[] args) throws Exception {
		authIfNeed();
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
		app.run(args);
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

	@Override
	public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Dimension screenSize = toolkit.getScreenSize();
		setSize((int) (screenSize.width * 0.625), (int) (screenSize.height * 0.75));
		setResizable(false);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setIconImage(toolkit.createImage(Main.class.getResource("/static/logo.png")));

		Browser browser = new Browser();
		add(new BrowserView(browser), BorderLayout.CENTER);
		setVisible(true);
		browser.loadURL("http://localhost:" + WEB_SERVER_PORT);
	}

	@RequestMapping(path = "/", method = RequestMethod.GET)
	public String connect(HttpSession session, ModelMap modelMap) {
		ZKClient zkClient = (ZKClient) session.getAttribute("ZK_CLIENT");
		if (zkClient != null) {
			return "redirect:/node";
		}
		modelMap.put("history", HISTORY_SET);
		return "connect";
	}

	@RequestMapping(path = "/", method = RequestMethod.POST)
	public void connect(HttpServletRequest request, HttpServletResponse response,
						  String connectString) throws Exception {
		if (!Regex.ZK_ADDRESS.matches(connectString)) {
			throw new RuntimeException("Invalid connect string: " + connectString);
		}

		ZKClient zkClient = new ZKClient(connectString, 30000);
		if (!zkClient.waitToConnected(3, TimeUnit.SECONDS)) {
			zkClient.destroy();
			throw new RuntimeException("Connect to " + connectString + " timeout");
		}
		request.getSession().setAttribute("ZK_CLIENT", zkClient);
		response.sendRedirect("/node");
		HISTORY_SET.removeIf(connectString::equals);
		HISTORY_SET.add(0, connectString);
	}

	@RequestMapping("/quit")
	public void quit(HttpSession session, HttpServletResponse response) throws IOException {
		ZKClient zkClient = (ZKClient) session.getAttribute("ZK_CLIENT");
		if (zkClient != null) {
			zkClient.destroy();
		}
		session.invalidate();
		setTitle(null);
		response.sendRedirect("/");
	}

	@RequestMapping(path = "/info/**")
	@ResponseBody
	public Object nodeInfo(HttpServletRequest request) throws Exception {
		String node = request.getRequestURI().substring(5);
		if (StringUtils.isBlank(node)) {
			node = "/";
		}
		setTitle(node);
		ZKClient zkClient = (ZKClient) request.getSession().getAttribute("ZK_CLIENT");
		List<String> children = zkClient.getChildren(node);
		Stat stat = new Stat();
		byte[] data = zkClient.getData(node, stat);
		Map<String, Object> model = new HashMap<>();
		model.put("children", children);
		model.put("data", data == null ? null : new String(data, StandardCharsets.UTF_8));
		model.put("stat", stat);
		return model;
	}

	@Configuration
	@SuppressWarnings("unused")
	static class MvcConfigurer extends WebMvcConfigurerAdapter {

		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			registry.addInterceptor(new HandlerInterceptorAdapter() {
				@Override
				public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
										 Object handler) throws Exception {
					HttpSession session = request.getSession();
					ZKClient zkClient = (ZKClient) session.getAttribute("ZK_CLIENT");
					if (zkClient == null) {
						response.sendRedirect("/");
						return false;
					}
					return true;
				}
			}).addPathPatterns("/node", "/info/**");
		}

		@Override
		public void addViewControllers(ViewControllerRegistry registry) {
			registry.addViewController("/node").setViewName("node");
		}

		@Override
		public void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {
			SimpleMappingExceptionResolver exceptionResolver = new SimpleMappingExceptionResolver() {
				@Override
				protected void logException(Exception ex, HttpServletRequest request) {
					logger.error("System error", ex);
				}
			};
			exceptionResolver.setDefaultStatusCode(500);
			exceptionResolver.setDefaultErrorView("error");
			exceptionResolvers.add(exceptionResolver);
		}
	}
}