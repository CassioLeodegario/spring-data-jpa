/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jpa.aot;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.AbstractAssert;
import org.springframework.aot.hint.ClassProxyHint;
import org.springframework.aot.hint.TypeReference;

/**
 * @author Christoph Strobl
 * @since 2022/04
 */
public class ClassProxyAssert extends AbstractAssert<ClassProxyAssert, ClassProxyHint> {

	protected ClassProxyAssert(ClassProxyHint classProxyHint) {
		super(classProxyHint, ClassProxyAssert.class);
	}

	public void matches(Class<?>... proxyInterfaces) {
		assertThat(actual.getProxiedInterfaces().stream().map(TypeReference::getCanonicalName))
				.containsExactly(Arrays.stream(proxyInterfaces).map(Class::getCanonicalName).toArray(String[]::new));
	}

	public List<TypeReference> getProxiedInterfaces() {
		return actual.getProxiedInterfaces();
	}
}