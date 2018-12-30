package pers.zyc.gui;

import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.swing.BrowserView;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

/**
 * @author zhangyancheng
 */
@SpringBootApplication
public class Launcher {

	public static void main(String[] args) throws Exception {
		JXBrowserAuth.authIfNeed();

		SpringApplication app = new SpringApplication(Launcher.class);
		app.setHeadless(false);
		app.setBannerMode(Banner.Mode.OFF);
		app.run(args);

		Browser browser = new Browser();
		BrowserView view = new BrowserView(browser);

		JFrame frame = new JFrame();
		frame.setType(JFrame.Type.UTILITY);//隐藏任务栏图标
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		frame.add(view, BorderLayout.CENTER);
		frame.setSize(1000, 800);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		browser.loadURL("http://localhost:9999");
	}

	@Configuration
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
