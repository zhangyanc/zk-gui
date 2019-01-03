package pers.zyc.zkgui;

import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.ba;
import com.teamdev.jxbrowser.chromium.swing.BrowserView;
import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.client.ConnectStringParser;
import org.apache.zookeeper.data.Stat;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author zhangyancheng
 */
@Controller
@SpringBootApplication
public class Main extends JFrame implements InitializingBean {

	public static void main(String[] args) throws Exception {
		authIfNeed();
		System.setProperty("java.awt.headless", "false");
		SpringApplication.run(Main.class, args);
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

	@Value("${frame.wight:1000}") private int wight;
	@Value("${frame.height:600}") private int height;
	@Value("${frame.resizable:false}") private boolean resizable;
	@Value("${frame.iconImage:/static/image/logo.png}") private String iconImage;
	@Value("http://localhost:${server.port:8080}") private String address;

	@Override
	public void afterPropertiesSet() throws Exception {
		//setTitle("ZooKeeper GUI");
		setSize(wight, height);
		setResizable(resizable);
		setType(Type.UTILITY);
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
			throw new RuntimeException("Invalid connect str: " + connectString);
		}

		ZKClient zkClient = new ZKClient(connectString, 30000);
		if (!zkClient.waitToConnected(5, TimeUnit.SECONDS)) {
			zkClient.destroy();
			throw new RuntimeException("Connect to " + connectString + " timeout");
		}
		request.getSession().setAttribute("ZK_CLIENT", zkClient);
		String rootPath = new ConnectStringParser(connectString).getChrootPath();
		if (rootPath == null) {
			rootPath = "";
		}
		response.sendRedirect("/ROOT" + rootPath);
	}

	@RequestMapping("/quit")
	public void quit(HttpSession session, HttpServletResponse response) throws IOException {
		ZKClient zkClient = (ZKClient) session.getAttribute("ZK_CLIENT");
		if (zkClient != null) {
			zkClient.destroy();
		}
		session.invalidate();
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
			ExceptionResolver exceptionResolver = new ExceptionResolver();
			IntStream.of(404, 500).forEach(i -> exceptionResolver.addStatusCode("error/" + i, i));
			Properties exceptionMappings = new Properties();
			exceptionMappings.put("org.springframework.web.servlet.NoHandlerFoundException", "error/404");
			exceptionMappings.put("java.lang.Throwable", "error/500");
			exceptionResolver.setExceptionMappings(exceptionMappings);
			exceptionResolvers.add(exceptionResolver);
		}
	}
}