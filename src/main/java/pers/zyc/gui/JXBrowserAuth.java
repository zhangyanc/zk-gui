package pers.zyc.gui;

import com.teamdev.jxbrowser.chromium.ba;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.stream.Stream;

/**
 * @author zhangyancheng
 */
class JXBrowserAuth {

	static void authIfNeed() throws Exception {
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
}
