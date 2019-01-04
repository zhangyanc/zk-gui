package pers.zyc.zkgui;

import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.ba;
import com.teamdev.jxbrowser.chromium.swing.BrowserView;
import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.EmbeddedServletContainerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
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
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * @author zhangyancheng
 */
@Controller
@SpringBootApplication
public class Main extends JFrame implements ApplicationListener<EmbeddedServletContainerInitializedEvent> {

	public static void main(String[] args) throws Exception {
		authIfNeed();
		SpringApplication app = new SpringApplication(Main.class);
		app.setHeadless(false);
		app.setBannerMode(Banner.Mode.OFF);
		app.setDefaultProperties(new HashMap<String, Object>() {
			{
				put("server.port", 9999);
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

	@Value("${frame.wight:1100}") private int wight;
	@Value("${frame.height:680}") private int height;
	@Value("${frame.resizable:false}") private boolean resizable;
	@Value("${frame.iconImage:/static/logo.png}") private String iconImage;
	@Value("http://localhost:${server.port:8080}") private String address;

	@Override
	public void onApplicationEvent(EmbeddedServletContainerInitializedEvent event) {
		setSize(wight, height);
		setResizable(resizable);
		//setType(Type.UTILITY);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setIconImage(Toolkit.getDefaultToolkit().createImage(Main.class.getResource(iconImage)));

		Browser browser = new Browser();
		browser.loadURL(address);
		add(new BrowserView(browser), BorderLayout.CENTER);
		setVisible(true);
	}

	@RequestMapping(path = "/", method = RequestMethod.GET)
	public String connect(HttpServletRequest request) {
		ZKClient zkClient = (ZKClient) request.getSession().getAttribute("ZK_CLIENT");
		if (zkClient != null) {
			return "redirect:/ROOT";
		}
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
		request.getRequestDispatcher("/ROOT").forward(request, response);
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

	@RequestMapping(path = {"/ROOT", "/ROOT/**"})
	public ModelAndView node(HttpServletRequest request) throws Exception {
		String path = request.getRequestURI().substring(5);

		if (StringUtils.isBlank(path)) {
			path = "/";
		} else if (path.charAt(path.length() - 1) == '/') {
			path = path.substring(0, path.length() - 1);
		}
		setTitle(path);

		ZKClient zkClient = (ZKClient) request.getSession().getAttribute("ZK_CLIENT");
		ModelAndView mav = new ModelAndView("node");
		mav.addObject("children", zkClient.getChildren(path));
		Stat stat = new Stat();
		byte[] data = zkClient.getData(path, stat);
		mav.addObject("data", data == null ? null : new String(data, StandardCharsets.UTF_8));
		mav.addObject("stat", stat);
		return mav;
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
			}).addPathPatterns("/ROOT", "/ROOT/**");
		}

		@Override
		public void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {
			SimpleMappingExceptionResolver exceptionResolver = new SimpleMappingExceptionResolver();
			exceptionResolver.setDefaultStatusCode(500);
			exceptionResolver.setDefaultErrorView("error");
			exceptionResolvers.add(exceptionResolver);
		}
	}
}