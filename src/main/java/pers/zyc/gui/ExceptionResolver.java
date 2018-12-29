package pers.zyc.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.event.Level;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * 异常处理器
 */
public class ExceptionResolver extends SimpleMappingExceptionResolver implements MessageSourceAware, InitializingBean {

	private final ObjectMapper om = new ObjectMapper();
	private MessageSourceAccessor messageSourceAccessor;

	@Override
	protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
											  Exception ex) {

		final ErrorObj error = parseError(request, ex);

		if (isNeedReturnJson(request)) {
			response.setContentType("application/json;charset=UTF-8");
			try (PrintWriter respnoseWriter = response.getWriter()) {
				om.writeValue(respnoseWriter, new HashMap<String, Object>(2) {
					{
						put("status", 0);
						put("error", error);
					}
				});
			} catch (IOException e) {
				logger.error(e.getMessage());
			}
			return new ModelAndView();
		}

		ModelAndView modelAndView = super.doResolveException(request, response, handler, ex);
		modelAndView.addObject("error", error);
		return modelAndView;
	}

	private ErrorObj parseError(HttpServletRequest request, Exception ex) {
        ErrorObj error = new ErrorObj();
        error.code(-1);

        List<Object> args = new ArrayList<>();
        Exception logException = null;
        Level level = Level.WARN;
        String errorMsg = "";
        String format = "";
        error.message(errorMsg);
        return error;
    }

	private boolean isNeedReturnJson(HttpServletRequest request) {
	    String acceptContentType = request.getHeader(HttpHeaders.ACCEPT);
	    return "XMLHttpRequest".equals(request.getHeader("X-Requested-With")) || (StringUtils.isNotBlank(acceptContentType) &&
                (acceptContentType.equals("text/javascript") || acceptContentType.equals("application/json")));
    }

	private String getErrorMessage(BindingResult bindingResult) {
	    String errorMsg = null;
        if (bindingResult.hasGlobalErrors()) {
            errorMsg = messageSourceAccessor.getMessage(bindingResult.getGlobalError().getCode(),
                    bindingResult.getGlobalError().getDefaultMessage());
        } else if (bindingResult.hasFieldErrors()) {
            errorMsg = bindingResult.getFieldError().getDefaultMessage();
        }
        return errorMsg;
    }

	@Override
	public void setMessageSource(MessageSource messageSource) {
		messageSourceAccessor = new MessageSourceAccessor(messageSource);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (messageSourceAccessor == null) {
			messageSourceAccessor = new MessageSourceAccessor(new MessageSource() {
				@Override
				public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
					return defaultMessage;
				}

				@Override
				public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
					return null;
				}

				@Override
				public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
					return resolvable.getDefaultMessage();
				}
			});
		}
	}

	public static class ErrorObj {
		private int code;
		private String message;

		public ErrorObj code(int code) {
			this.code = code;
			return this;
		}

		public ErrorObj message(String message) {
			this.message = message;
			return this;
		}

		public int getCode() {
			return code;
		}

		public String getMessage() {
			return message;
		}
	}
}
