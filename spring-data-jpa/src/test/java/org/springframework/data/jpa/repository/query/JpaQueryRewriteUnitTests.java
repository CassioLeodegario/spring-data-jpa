/*
 * Copyright 2008-2022 the original author or authors.
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
package org.springframework.data.jpa.repository.query;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Metamodel;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.sample.User;
import org.springframework.data.jpa.provider.QueryExtractor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryRewrite;
import org.springframework.data.jpa.repository.QueryRewriter;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.DefaultRepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.RepositoryQuery;

/**
 * Unit tests for repository with {@link Query} and {@link QueryRewrite}.
 * 
 * @author Greg Turnquist
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class JpaQueryRewriteUnitTests {

	private static final QueryMethodEvaluationContextProvider EVALUATION_CONTEXT_PROVIDER = QueryMethodEvaluationContextProvider.DEFAULT;

	@Mock EntityManager em;
	@Mock EntityManagerFactory emf;
	@Mock QueryExtractor extractor;
	@Mock NamedQueries namedQueries;
	@Mock Metamodel metamodel;
	@Mock ProjectionFactory projectionFactory;

	private JpaQueryMethodFactory queryMethodFactory;
	private QueryLookupStrategy strategy;
	private RepositoryMetadata metadata;

	// Results
	static final String ORIGINAL_QUERY = "original query";
	static final String REWRITTEN_QUERY = "rewritten query";
	static final String SORT = "sort";
	static Map<String, String> results = new HashMap<>();

	@BeforeEach
	void setUp() {

		when(em.getMetamodel()).thenReturn(metamodel);
		when(em.getEntityManagerFactory()).thenReturn(emf);
		when(emf.createEntityManager()).thenReturn(em);
		when(em.getDelegate()).thenReturn(em);

		this.queryMethodFactory = new DefaultJpaQueryMethodFactory(extractor);
		this.strategy = JpaQueryLookupStrategy.create(em, queryMethodFactory, QueryLookupStrategy.Key.CREATE_IF_NOT_FOUND,
				EVALUATION_CONTEXT_PROVIDER, EscapeCharacter.DEFAULT);
		this.metadata = new DefaultRepositoryMetadata(UserRepository.class);

		this.results.clear();
	}

	@Test
	void nativeQueryShouldHandleRewrites() throws NoSuchMethodException {

		// given
		when(em.createNativeQuery(anyString(), (Class<?>) any())).thenReturn(mock(jakarta.persistence.Query.class));
		Method method = UserRepository.class.getMethod("findByNativeQuery", String.class);

		// when
		RepositoryQuery query = strategy.resolveQuery(method, metadata, projectionFactory, namedQueries);

		// then
		assertThat(query).isInstanceOf(NativeJpaQuery.class);
		NativeJpaQuery nativeJpaQuery = (NativeJpaQuery) query;

		// when
		nativeJpaQuery.doCreateQuery(
				new JpaParametersParameterAccessor(query.getQueryMethod().getParameters(), new Object[] { "Matthews" }));

		// then
		assertThat(results).containsExactly( //
				entry(ORIGINAL_QUERY, "select u.* from User u"), //
				entry(REWRITTEN_QUERY, "select user.* from User user"), //
				entry(SORT, Sort.unsorted().toString()));
	}

	@Test
	void nonNativeQueryShouldHandleRewrites() throws NoSuchMethodException {

		// given
		when(em.createQuery(anyString())).thenReturn(mock(jakarta.persistence.Query.class));
		Method method = UserRepository.class.getMethod("findByNonNativeQuery", String.class);

		// when
		RepositoryQuery query = strategy.resolveQuery(method, metadata, projectionFactory, namedQueries);

		// then
		assertThat(query).isInstanceOf(SimpleJpaQuery.class);
		SimpleJpaQuery simpleJpaQuery = (SimpleJpaQuery) query;

		// when
		simpleJpaQuery.doCreateQuery(
				new JpaParametersParameterAccessor(query.getQueryMethod().getParameters(), new Object[] { "Matthews" }));

		// then
		assertThat(results).containsExactly( //
				entry(ORIGINAL_QUERY, "select u.* from User u"), //
				entry(REWRITTEN_QUERY, "select user.* from User user"), //
				entry(SORT, Sort.unsorted().toString()));
	}

	@Test
	void nonNativeQueryWithSortShouldHandleRewrites() throws NoSuchMethodException {

		// given
		when(em.createQuery(anyString())).thenReturn(mock(jakarta.persistence.Query.class));
		Method method = UserRepository.class.getMethod("findByNonNativeSortedQuery", String.class, Sort.class);

		// when
		RepositoryQuery query = strategy.resolveQuery(method, metadata, projectionFactory, namedQueries);

		// then
		assertThat(query).isInstanceOf(SimpleJpaQuery.class);
		SimpleJpaQuery simpleJpaQuery = (SimpleJpaQuery) query;

		// when
		simpleJpaQuery.doCreateQuery(new JpaParametersParameterAccessor(query.getQueryMethod().getParameters(),
				new Object[] { "Matthews", Sort.by("lastname") }));

		// then
		assertThat(results).containsExactly( //
				entry(ORIGINAL_QUERY, "select u.* from User u order by u.lastname asc"), //
				entry(REWRITTEN_QUERY, "select user.* from User user order by user.lastname asc"), //
				entry(SORT, Sort.by("lastname").ascending().toString()));

		// when
		simpleJpaQuery.doCreateQuery(new JpaParametersParameterAccessor(query.getQueryMethod().getParameters(),
				new Object[] { "Matthews", Sort.by("firstname").descending() }));

		// then
		assertThat(results).containsExactly( //
				entry(ORIGINAL_QUERY, "select u.* from User u order by u.firstname desc"), //
				entry(REWRITTEN_QUERY, "select user.* from User user order by user.firstname desc"), //
				entry(SORT, Sort.by("firstname").descending().toString()));
	}

	@Test
	void nonNativeQueryWithPageableShouldHandleRewrites() throws NoSuchMethodException {

		// given
		when(em.createQuery(anyString())).thenReturn(mock(jakarta.persistence.Query.class));
		Method method = UserRepository.class.getMethod("findByNonNativePagedQuery", String.class, Pageable.class);

		// when
		RepositoryQuery query = strategy.resolveQuery(method, metadata, projectionFactory, namedQueries);

		// then
		assertThat(query).isInstanceOf(SimpleJpaQuery.class);
		SimpleJpaQuery simpleJpaQuery = (SimpleJpaQuery) query;

		// when
		simpleJpaQuery.doCreateQuery(new JpaParametersParameterAccessor(query.getQueryMethod().getParameters(),
				new Object[] { "Matthews", PageRequest.of(2, 1) }));

		// then
		assertThat(results).containsExactly( //
				entry(ORIGINAL_QUERY, "select u.* from User u"), //
				entry(REWRITTEN_QUERY, "select user.* from User user"), //
				entry(SORT, Sort.unsorted().toString()));
	}

	@Test
	void nativeQueryWithNoRewriteAnnotationShouldNotDoRewrites() throws NoSuchMethodException {

		// given
		when(em.createNativeQuery(anyString(), (Class<?>) any())).thenReturn(mock(jakarta.persistence.Query.class));
		Method method = UserRepository.class.getMethod("findByNativeQueryWithNoRewrite", String.class);

		// when
		RepositoryQuery query = strategy.resolveQuery(method, metadata, projectionFactory, namedQueries);

		// then
		assertThat(query).isInstanceOf(NativeJpaQuery.class);
		NativeJpaQuery nativeJpaQuery = (NativeJpaQuery) query;

		// when
		nativeJpaQuery.doCreateQuery(
				new JpaParametersParameterAccessor(query.getQueryMethod().getParameters(), new Object[] { "Matthews" }));

		// then
		assertThat(results).isEmpty();
	}

	@Test
	void nonNativeQueryWithNoRewriteAnnotationShouldNotDoRewrites() throws NoSuchMethodException {

		// given
		when(em.createQuery(anyString())).thenReturn(mock(jakarta.persistence.Query.class));
		Method method = UserRepository.class.getMethod("findByNonNativeQueryWithNoRewrite", String.class);

		// when
		RepositoryQuery query = strategy.resolveQuery(method, metadata, projectionFactory, namedQueries);

		// then
		assertThat(query).isInstanceOf(SimpleJpaQuery.class);
		SimpleJpaQuery simpleJpaQuery = (SimpleJpaQuery) query;

		// when
		simpleJpaQuery.doCreateQuery(
				new JpaParametersParameterAccessor(query.getQueryMethod().getParameters(), new Object[] { "Matthews" }));

		// then
		assertThat(results).isEmpty();
	}

	public interface UserRepository extends Repository<User, Integer> {

		@Query(value = "select u.* from User u", nativeQuery = true)
		@QueryRewrite(TestQueryRewriter.class)
		List<User> findByNativeQuery(String param);

		@Query(value = "select u.* from User u")
		@QueryRewrite(TestQueryRewriter.class)
		List<User> findByNonNativeQuery(String param);

		@Query(value = "select u.* from User u")
		@QueryRewrite(TestQueryRewriter.class)
		List<User> findByNonNativeSortedQuery(String param, Sort sort);

		@Query(value = "select u.* from User u")
		@QueryRewrite(TestQueryRewriter.class)
		List<User> findByNonNativePagedQuery(String param, Pageable pageable);

		@Query(value = "select u.* from User u", nativeQuery = true)
		List<User> findByNativeQueryWithNoRewrite(String param);

		@Query(value = "select u.* from User u")
		List<User> findByNonNativeQueryWithNoRewrite(String param);
	}

	static class TestQueryRewriter implements QueryRewriter {

		@Override
		public String rewrite(String query, Sort sort) {

			String rewrittenQuery = query.replaceAll("u", "user");

			// Capture results for testing.
			results.put(ORIGINAL_QUERY, query);
			results.put(REWRITTEN_QUERY, rewrittenQuery);
			results.put(SORT, sort.toString());

			return rewrittenQuery;
		}
	}
}
