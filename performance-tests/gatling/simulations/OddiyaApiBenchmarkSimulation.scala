import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Oddiya API Benchmark Simulation
 * 
 * This simulation provides precise benchmarking of individual API endpoints
 * with strict SLA validation. Each endpoint is tested with different load
 * patterns to establish performance baselines.
 * 
 * SLA Targets:
 * - Authentication: <100ms (95th percentile)
 * - Search Operations: <200ms (95th percentile) 
 * - Travel Plans: <300ms (95th percentile)
 * - Health Checks: <50ms (95th percentile)
 */
class OddiyaApiBenchmarkSimulation extends Simulation {

  // Configuration
  val baseUrl = System.getProperty("base.url", "http://localhost:8080")
  val apiVersion = "/api/v1"
  val benchmarkDuration = Integer.getInteger("benchmark.duration", 10).toInt.minutes
  val warmupDuration = Integer.getInteger("warmup.duration", 2).toInt.minutes
  val usersPerEndpoint = Integer.getInteger("users.per.endpoint", 20)

  // HTTP Protocol Configuration
  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling-ApiBenchmark/1.0")
    .shareConnections
    .disableCaching

  // Data Feeders
  val userCredentials = csv("user-credentials.csv").circular
  val searchQueries = csv("search-queries.csv").circular

  // Benchmark Operations
  object BenchmarkOperations {
    
    // Authentication Benchmarks (Target: <100ms)
    val authBenchmark = exec(
      http("AUTH BENCHMARK - Login")
        .post(s"$apiVersion/auth/login")
        .body(StringBody("""{
          "provider": "google",
          "providerToken": "benchmark_token_#{randomLong()}",
          "email": "benchmark_#{userId}@oddiya.com",
          "name": "Benchmark User #{userId}"
        }""")).asJson
        .check(status.is(200))
        .check(responseTimeInMillis.lte(100))
        .check(jsonPath("$.data.accessToken").saveAs("accessToken"))
        .check(jsonPath("$.success").is("true"))
    )

    val authRefreshBenchmark = exec(
      http("AUTH BENCHMARK - Refresh Token")
        .post(s"$apiVersion/auth/refresh")
        .header("Authorization", "Bearer ${accessToken}")
        .body(StringBody("""{
          "refreshToken": "benchmark_refresh_#{randomLong()}"
        }""")).asJson
        .check(status.is(200))
        .check(responseTimeInMillis.lte(80))
    )

    // Search Benchmarks (Target: <200ms)
    val placesSearchBenchmark = exec(
      http("SEARCH BENCHMARK - Places Search")
        .get(s"$apiVersion/places/search")
        .queryParam("query", "${searchQuery}")
        .queryParam("limit", "20")
        .queryParam("radius", "5000")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(200))
        .check(jsonPath("$.data").exists)
        .check(jsonPath("$.data[*].id").count.gte(1))
    )

    val placesPopularBenchmark = exec(
      http("SEARCH BENCHMARK - Popular Places")
        .get(s"$apiVersion/places/popular")
        .queryParam("category", "${category}")
        .queryParam("limit", "20")
        .queryParam("sort", "rating")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(150))
        .check(jsonPath("$.data").exists)
    )

    val placesDetailBenchmark = exec(
      http("SEARCH BENCHMARK - Place Details")
        .get(s"$apiVersion/places/#{randomInt(1, 1000)}")
        .check(status.in(200, 404))
        .check(responseTimeInMillis.lte(100))
    )

    // Travel Plan Benchmarks (Target: <300ms)
    val travelPlanCreateBenchmark = exec(
      http("TRAVEL PLAN BENCHMARK - Create")
        .post(s"$apiVersion/travel-plans")
        .header("Authorization", "Bearer ${accessToken}")
        .body(StringBody("""{
          "title": "Benchmark Plan #{__time()}",
          "destination": "${searchQuery}",
          "startDate": "2024-06-01",
          "endDate": "2024-06-05",
          "budget": 1500000,
          "travelers": 2,
          "preferences": ["문화체험", "맛집투어"],
          "accommodation": "호텔",
          "transportation": "대중교통"
        }""")).asJson
        .check(status.is(201))
        .check(responseTimeInMillis.lte(300))
        .check(jsonPath("$.data.id").saveAs("travelPlanId"))
    )

    val travelPlanListBenchmark = exec(
      http("TRAVEL PLAN BENCHMARK - List")
        .get(s"$apiVersion/travel-plans")
        .header("Authorization", "Bearer ${accessToken}")
        .queryParam("limit", "20")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(200))
        .check(jsonPath("$.data").exists)
    )

    val travelPlanDetailBenchmark = exec(
      http("TRAVEL PLAN BENCHMARK - Details")
        .get(s"$apiVersion/travel-plans/" + "${travelPlanId}")
        .header("Authorization", "Bearer ${accessToken}")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(150))
        .check(jsonPath("$.data.id").exists)
    )

    val travelPlanUpdateBenchmark = exec(
      http("TRAVEL PLAN BENCHMARK - Update")
        .put(s"$apiVersion/travel-plans/" + "${travelPlanId}")
        .header("Authorization", "Bearer ${accessToken}")
        .body(StringBody("""{
          "title": "Updated Benchmark Plan #{__time()}",
          "budget": 2000000
        }""")).asJson
        .check(status.is(200))
        .check(responseTimeInMillis.lte(250))
    )

    // Health Check Benchmarks (Target: <50ms)
    val healthBenchmark = exec(
      http("HEALTH BENCHMARK - Basic Health")
        .get("/actuator/health")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(50))
        .check(jsonPath("$.status").is("UP"))
    )

    val readinessBenchmark = exec(
      http("HEALTH BENCHMARK - Readiness")
        .get("/actuator/health/readiness")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(30))
    )

    val livenessBenchmark = exec(
      http("HEALTH BENCHMARK - Liveness")
        .get("/actuator/health/liveness")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(20))
    )
  }

  // Benchmark Scenarios
  val authBenchmarkScenario = scenario("Authentication Benchmark")
    .feed(userCredentials)
    .exec(BenchmarkOperations.authBenchmark)
    .pause(500.milliseconds, 1)
    .exec(BenchmarkOperations.authRefreshBenchmark)
    .pause(500.milliseconds, 1)

  val searchBenchmarkScenario = scenario("Search Benchmark")
    .feed(searchQueries)
    .exec(BenchmarkOperations.placesSearchBenchmark)
    .pause(300.milliseconds, 800.milliseconds)
    .exec(BenchmarkOperations.placesPopularBenchmark)
    .pause(300.milliseconds, 600.milliseconds)
    .exec(BenchmarkOperations.placesDetailBenchmark)

  val travelPlanBenchmarkScenario = scenario("Travel Plan Benchmark")
    .feed(userCredentials)
    .exec(BenchmarkOperations.authBenchmark) // Need auth for travel plans
    .pause(200.milliseconds)
    .feed(searchQueries)
    .exec(BenchmarkOperations.travelPlanCreateBenchmark)
    .pause(500.milliseconds, 1)
    .exec(BenchmarkOperations.travelPlanDetailBenchmark)
    .pause(300.milliseconds, 700.milliseconds)
    .exec(BenchmarkOperations.travelPlanListBenchmark)
    .pause(300.milliseconds, 500.milliseconds)
    .exec(BenchmarkOperations.travelPlanUpdateBenchmark)

  val healthBenchmarkScenario = scenario("Health Check Benchmark")
    .exec(BenchmarkOperations.healthBenchmark)
    .pause(100.milliseconds, 300.milliseconds)
    .exec(BenchmarkOperations.readinessBenchmark)
    .pause(100.milliseconds, 200.milliseconds)
    .exec(BenchmarkOperations.livenessBenchmark)

  // Combined benchmark scenario for realistic load
  val fullApiBenchmarkScenario = scenario("Full API Benchmark")
    .feed(userCredentials)
    .exec(BenchmarkOperations.authBenchmark)
    .pause(1, 2)
    .feed(searchQueries)
    .exec(BenchmarkOperations.placesSearchBenchmark)
    .pause(1, 3)
    .exec(BenchmarkOperations.placesPopularBenchmark)
    .pause(1, 2)
    .exec(BenchmarkOperations.travelPlanCreateBenchmark)
    .pause(1, 3)
    .exec(BenchmarkOperations.travelPlanDetailBenchmark)
    .pause(1, 2)
    .exec(BenchmarkOperations.healthBenchmark)

  // Benchmark Test Setup
  setUp(
    // Individual endpoint benchmarks
    authBenchmarkScenario.inject(
      constantUsersPerSec(usersPerEndpoint / 10.0).during(benchmarkDuration)
    ),
    
    searchBenchmarkScenario.inject(
      constantUsersPerSec(usersPerEndpoint / 8.0).during(benchmarkDuration)
    ),
    
    travelPlanBenchmarkScenario.inject(
      constantUsersPerSec(usersPerEndpoint / 15.0).during(benchmarkDuration)
    ),
    
    healthBenchmarkScenario.inject(
      constantUsersPerSec(usersPerEndpoint / 5.0).during(benchmarkDuration)
    ),
    
    // Full API benchmark
    fullApiBenchmarkScenario.inject(
      rampUsers(usersPerEndpoint).during(warmupDuration),
      constantUsersPerSec(usersPerEndpoint / 20.0).during(benchmarkDuration)
    )
  ).protocols(httpProtocol)
    .maxDuration(benchmarkDuration + warmupDuration)
    .assertions(
      // Global performance requirements
      global.responseTime.max.lt(2000),
      global.responseTime.mean.lt(500),
      global.successfulRequests.percent.gt(99.5),
      
      // Authentication SLA: <100ms (95th percentile)
      details("AUTH BENCHMARK - Login").responseTime.percentile3.lt(100),
      details("AUTH BENCHMARK - Login").successfulRequests.percent.gt(99.9),
      details("AUTH BENCHMARK - Refresh Token").responseTime.percentile3.lt(80),
      
      // Search SLA: <200ms (95th percentile)
      details("SEARCH BENCHMARK - Places Search").responseTime.percentile3.lt(200),
      details("SEARCH BENCHMARK - Places Search").successfulRequests.percent.gt(99.5),
      details("SEARCH BENCHMARK - Popular Places").responseTime.percentile3.lt(150),
      details("SEARCH BENCHMARK - Place Details").responseTime.percentile3.lt(100),
      
      // Travel Plan SLA: <300ms (95th percentile)
      details("TRAVEL PLAN BENCHMARK - Create").responseTime.percentile3.lt(300),
      details("TRAVEL PLAN BENCHMARK - Create").successfulRequests.percent.gt(99),
      details("TRAVEL PLAN BENCHMARK - List").responseTime.percentile3.lt(200),
      details("TRAVEL PLAN BENCHMARK - Details").responseTime.percentile3.lt(150),
      details("TRAVEL PLAN BENCHMARK - Update").responseTime.percentile3.lt(250),
      
      // Health Check SLA: <50ms (95th percentile)
      details("HEALTH BENCHMARK - Basic Health").responseTime.percentile3.lt(50),
      details("HEALTH BENCHMARK - Basic Health").successfulRequests.percent.gt(99.9),
      details("HEALTH BENCHMARK - Readiness").responseTime.percentile3.lt(30),
      details("HEALTH BENCHMARK - Liveness").responseTime.percentile3.lt(20)
    )
    .before {
      println(s"""
        |========================================
        |  Oddiya API Benchmark Configuration
        |========================================
        |  Base URL: $baseUrl
        |  Benchmark Duration: $benchmarkDuration
        |  Warmup Duration: $warmupDuration
        |  Users per Endpoint: $usersPerEndpoint
        |  
        |  SLA Targets:
        |  - Authentication: <100ms (95th percentile)
        |  - Search Operations: <200ms (95th percentile)
        |  - Travel Plans: <300ms (95th percentile) 
        |  - Health Checks: <50ms (95th percentile)
        |  
        |  Benchmark Categories:
        |  1. Authentication (Login, Refresh)
        |  2. Search (Places Search, Popular, Details)
        |  3. Travel Plans (CRUD operations)
        |  4. Health Checks (Health, Readiness, Liveness)
        |  5. Full API (Combined realistic usage)
        |  
        |  Success Criteria:
        |  - 99.5%+ success rate overall
        |  - All endpoints meet SLA targets
        |  - No performance degradation over time
        |  - Consistent response times
        |========================================
      """.stripMargin)
    }
    .after {
      println(s"""
        |========================================
        |  API Benchmark Analysis Guidelines
        |========================================
        |  
        |  Performance Metrics to Review:
        |  
        |  1. Response Time Analysis
        |     - Mean, 95th, 99th percentiles per endpoint
        |     - Compare against SLA targets
        |     - Identify performance outliers
        |     - Check for time-based degradation
        |  
        |  2. Throughput Analysis
        |     - Requests per second per endpoint
        |     - Transaction capacity under load
        |     - Concurrent user handling capability
        |  
        |  3. SLA Compliance
        |     ✓ Authentication <100ms (95th percentile)
        |     ✓ Search <200ms (95th percentile)
        |     ✓ Travel Plans <300ms (95th percentile)
        |     ✓ Health Checks <50ms (95th percentile)
        |  
        |  4. Error Analysis
        |     - Success rate per endpoint type
        |     - Error patterns and distributions
        |     - Timeout vs application errors
        |  
        |  5. Baseline Establishment
        |     - Use results as performance baseline
        |     - Set up monitoring alerts based on thresholds
        |     - Create performance regression tests
        |  
        |  Red Flags:
        |  - Any SLA target violations
        |  - Success rate below 99%
        |  - High variance in response times
        |  - Performance degradation over test duration
        |========================================
      """.stripMargin)
    }
}