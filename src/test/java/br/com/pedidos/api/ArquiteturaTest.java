package br.com.pedidos.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Guarda as regras de arquitetura do AGENTS.md e das specs 01 e 02.
 * Se este teste falhar, corrija o código. Só altere as regras com pedido explícito.
 */
class ArquiteturaTest {

	private static final Path MAIN = Path.of("src/main/java/br/com/pedidos/api");
	private static final Path TEST = Path.of("src/test/java/br/com/pedidos/api");

	private static final Pattern SPRING = Pattern.compile("org\\.springframework");
	private static final Pattern JAKARTA = Pattern.compile("jakarta\\.");
	private static final Pattern JAVAX = Pattern.compile("javax\\.");
	private static final Pattern LOMBOK = Pattern.compile("lombok");
	private static final Pattern HTTP = Pattern.compile("java\\.net\\.");
	private static final Pattern PONTO_FLUTUANTE = Pattern.compile("\\b(double|float|Double|Float)\\b");
	private static final Pattern SPRING_BOOT_TEST = Pattern.compile("SpringBootTest");
	private static final Pattern MOCKITO = Pattern.compile("(?i)mockito");
	private static final Pattern IMPORTA_ADAPTER = importaDe("adapter");
	private static final Pattern IMPORTA_APPLICATION = importaDe("application");

	private static final List<String> DEPENDENCIAS_APROVADAS = List.of(
			"spring-boot-starter-data-jpa",
			"spring-boot-starter-validation",
			"spring-boot-starter-webmvc",
			"postgresql",
			"spring-boot-starter-data-jpa-test",
			"spring-boot-starter-validation-test",
			"spring-boot-starter-webmvc-test");

	private static Pattern importaDe(String pacote) {
		return Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?br\\.com\\.pedidos\\.api\\." + pacote + "\\b");
	}

	private static Map<String, Pattern> regrasDoDominio() {
		Map<String, Pattern> regras = new LinkedHashMap<>();
		regras.put("usa Spring", SPRING);
		regras.put("usa Jakarta (JPA, Servlet, Validation)", JAKARTA);
		regras.put("usa javax", JAVAX);
		regras.put("usa Lombok", LOMBOK);
		regras.put("importa adapter", IMPORTA_ADAPTER);
		regras.put("importa application", IMPORTA_APPLICATION);
		regras.put("usa double ou float", PONTO_FLUTUANTE);
		return regras;
	}

	private static Map<String, Pattern> regrasDaAplicacao() {
		Map<String, Pattern> regras = new LinkedHashMap<>();
		regras.put("usa Spring", SPRING);
		regras.put("usa Jakarta (JPA, Servlet, Validation)", JAKARTA);
		regras.put("usa javax", JAVAX);
		regras.put("usa Lombok", LOMBOK);
		regras.put("usa HTTP (java.net)", HTTP);
		regras.put("importa adapter", IMPORTA_ADAPTER);
		return regras;
	}

	private static List<Path> arquivosJava(Path pasta) throws IOException {
		try (Stream<Path> arquivos = Files.walk(pasta)) {
			return arquivos.filter(arquivo -> arquivo.toString().endsWith(".java")).toList();
		}
	}

	private static List<String> violacoes(Path pasta, Map<String, Pattern> regras) throws IOException {
		List<String> achados = new ArrayList<>();
		for (Path arquivo : arquivosJava(pasta)) {
			String texto = Files.readString(arquivo);
			for (Map.Entry<String, Pattern> regra : regras.entrySet()) {
				if (regra.getValue().matcher(texto).find()) {
					achados.add(arquivo.getFileName() + " " + regra.getKey());
				}
			}
		}
		return achados;
	}

	@Test
	@DisplayName("as pastas verificadas existem e têm código, para o teste não passar no vazio")
	void pastasVerificadasTemCodigo() throws IOException {
		assertThat(arquivosJava(MAIN.resolve("domain"))).isNotEmpty();
		assertThat(arquivosJava(MAIN.resolve("application"))).isNotEmpty();
		assertThat(arquivosJava(TEST.resolve("domain"))).isNotEmpty();
		assertThat(arquivosJava(TEST.resolve("application"))).isNotEmpty();
	}

	@Test
	@DisplayName("domain é Java puro: sem frameworks, sem camadas externas e sem double ou float")
	void dominioEJavaPuro() throws IOException {
		assertThat(violacoes(MAIN.resolve("domain"), regrasDoDominio())).isEmpty();
	}

	@Test
	@DisplayName("application não depende de frameworks, de HTTP nem de adapters")
	void aplicacaoNaoDependeDeFora() throws IOException {
		assertThat(violacoes(MAIN.resolve("application"), regrasDaAplicacao())).isEmpty();
	}

	@Test
	@DisplayName("testes de domain e application rodam sem Spring, e os de application sem Mockito")
	void testesDoNucleoNaoUsamSpring() throws IOException {
		Map<String, Pattern> semSpring = Map.of("usa Spring", SPRING, "usa @SpringBootTest", SPRING_BOOT_TEST);
		Map<String, Pattern> semSpringNemMockito = Map.of(
				"usa Spring", SPRING, "usa @SpringBootTest", SPRING_BOOT_TEST, "usa Mockito", MOCKITO);

		assertThat(violacoes(TEST.resolve("domain"), semSpring)).isEmpty();
		assertThat(violacoes(TEST.resolve("application"), semSpringNemMockito)).isEmpty();
	}

	@Test
	@DisplayName("o pom.xml tem só as dependências aprovadas; dependência nova exige pedido explícito")
	void dependenciasDoPomSaoAsAprovadas() throws Exception {
		Document pom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of("pom.xml").toFile());
		NodeList artefatos = (NodeList) XPathFactory.newInstance().newXPath()
				.evaluate("/project/dependencies/dependency/artifactId", pom, XPathConstants.NODESET);
		List<String> encontradas = new ArrayList<>();
		for (int i = 0; i < artefatos.getLength(); i++) {
			encontradas.add(artefatos.item(i).getTextContent().trim());
		}

		assertThat(encontradas).containsExactlyInAnyOrderElementsOf(DEPENDENCIAS_APROVADAS);
	}

	@Test
	@DisplayName("o verificador acusa violações de verdade (teste do próprio teste)")
	void verificadorAcusaViolacoes(@TempDir Path pasta) throws IOException {
		Files.writeString(pasta.resolve("Ruim.java"), """
				package x;
				import org.springframework.stereotype.Service;
				import jakarta.persistence.Entity;
				import java.net.URI;
				import br.com.pedidos.api.adapter.Coisa;
				import br.com.pedidos.api.application.Outra;
				class Ruim { double preco; }
				""");
		Files.writeString(pasta.resolve("Boa.java"), """
				package x;
				import java.math.BigDecimal;
				class Boa { BigDecimal preco; }
				""");

		assertThat(violacoes(pasta, regrasDoDominio()))
				.containsExactly(
						"Ruim.java usa Spring",
						"Ruim.java usa Jakarta (JPA, Servlet, Validation)",
						"Ruim.java importa adapter",
						"Ruim.java importa application",
						"Ruim.java usa double ou float");
		assertThat(violacoes(pasta, regrasDaAplicacao()))
				.contains("Ruim.java usa HTTP (java.net)")
				.noneMatch(achado -> achado.startsWith("Boa.java"));
	}

}
