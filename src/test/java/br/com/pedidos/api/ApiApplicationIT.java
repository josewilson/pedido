package br.com.pedidos.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ApiApplicationIT {

	@Autowired
	private JdbcTemplate jdbc;

	// J9: confere o banco depois de o contexto subir. Não impede o ddl-auto de criar tabelas no startup.
	@BeforeEach
	void garanteQueEstaNoBancoPedidos() {
		assertThat(jdbc.queryForObject("select current_database()", String.class))
				.as("os testes de integração só podem rodar no banco 'pedidos'")
				.isEqualTo("pedidos");
	}

	@Test
	void contextLoads() {
	}

}
