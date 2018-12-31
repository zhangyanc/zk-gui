package pers.zyc.zkgui;

import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.ba;
import com.teamdev.jxbrowser.chromium.swing.BrowserView;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.List;
import java.util.Properties;
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

	@Value("${frame.wight}") private int wight;
	@Value("${frame.height}") private int height;
	@Value("${frame.resizable}") private boolean resizable;
	@Value("${frame.iconImage}") private String iconImage;
	@Value("http://localhost:${server.port}") private String address;

	@Override
	public void afterPropertiesSet() throws Exception {
		//setTitle("ZK GUI");
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

	@Configuration
	@SuppressWarnings("unused")
	static class MvcConfigurer extends WebMvcConfigurerAdapter {

		@Override
		public void addViewControllers(ViewControllerRegistry registry) {
			registry.addViewController("/").setViewName("index");
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
