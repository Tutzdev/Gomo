package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.supermercados.prices.support.PostgresTestDatabase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PricesApplicationTests {

	@Autowired
	JdbcTemplate jdbc;

	@LocalServerPort
	int port;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		PostgresTestDatabase.register(registry);
	}

	@Test
	void contextLoadsWithVerifiedStoreDirectoryAndNoInventedProductsOrPrices() {
		assertThat(jdbc.queryForObject("select count(*) from cities", Integer.class)).isEqualTo(4);
		// V52: the Barra Mansa markets without an online store exist from the start, with no price until a visit.
		assertThat(jdbc.queryForObject("select count(*) from stores where price_channel = 'IN_STORE'", Integer.class))
				.isEqualTo(5);
		assertThat(jdbc.queryForObject("select count(*) from stores where price_channel = 'ONLINE'", Integer.class))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("select source_reference from stores where price_channel = 'ONLINE'", String.class))
				.isEqualTo("https://redesupermarket.com.br/lojas/supermarket-aterrado/");
		// Kept for its history but retired by V34: online markets only enter with a collector.
		assertThat(jdbc.queryForObject("select active from stores where price_channel = 'ONLINE'", Boolean.class)).isFalse();
		assertThat(jdbc.queryForObject("select price_source_note from stores where price_channel = 'ONLINE'", String.class))
				.contains("Retirado em 10/2026");
		assertThat(jdbc.queryForObject("select count(*) from products", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from price_records", Integer.class)).isZero();
	}

	@Test
	void embeddedServerServesTheApiOverHttp() throws Exception {
		try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
			var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/cities"))
					.timeout(Duration.ofSeconds(10)).GET().build();
			var response = client.send(request, HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.headers().firstValue("content-type")).hasValueSatisfying(
					value -> assertThat(value).contains("application/json"));
			assertThat(response.body()).contains("Rio de Janeiro", "Volta Redonda", "Barra Mansa", "Resende");
		}
	}

}
