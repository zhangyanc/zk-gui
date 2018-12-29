package pers.zyc.gui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

/**
 * @author zhangyancheng
 */
@SpringBootApplication
public class Launcher {

	public static void main(String[] args) {
		SpringApplication.run(Launcher.class, args);
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
