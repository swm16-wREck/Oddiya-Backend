import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Oddiya Spike Test Simulation
 * 
 * This simulation tests how the system handles sudden spikes in traffic.
 * It follows a pattern: baseline → spike → recovery, measuring how the system
 * responds to sudden increases and decreases in load.
 * 
 * Pattern: 10 users → 500 users → 10 users
 * Goal: Test auto-scaling and recovery capabilities
 */
class OddiyaSpikeTestSimulation extends Simulation {

  // Configuration
  val baseUrl = System.getProperty("base.url", "http://localhost:8080")
  val apiVersion = "/api/v1"
  val baselineUsers = Integer.getInteger("baseline.users", 10)
  val spikeUsers = Integer.getInteger("spike.users", 500)
  val baselineDuration = Integer.getInteger("baseline.duration", 5).toInt.minutes
  val spikeDuration = Integer.getInteger("spike.duration", 2).toInt.minutes
  val recoveryDuration = Integer.getInteger("recovery.duration", 10).toInt.minutes

  // HTTP Protocol Configuration
  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling-SpikeTest/1.0")
    .shareConnections
    .maxConnectionsPerHost(600)

  // Data Feeders
  val userCredentials = csv("user-credentials.csv").circular
  val searchQueries = csv("search-queries.csv").circular

  // Spike Test Operations
  object SpikeOperations {
    val authenticateAndSearch = exec(
      http("Auth + Search Combo")
        .post(s"$apiVersion/auth/login")
        .body(StringBody("""{
          "provider": "google",
          "providerToken": "spike_test_#{randomLong()}",
          "email": "spike_user_#{randomInt(1, 1000)}@oddiya.com",
          "name": "Spike Test User #{randomInt(1, 1000)}"
        }""")).asJson
        .check(status.in(200, 429))
        .check(responseTimeInMillis.lte(2000))
        .check(jsonPath("$.data.accessToken").optional.saveAs("accessToken"))
    ).exec(
      http("Immediate Search After Auth")
        .get(s"$apiVersion/places/search")
        .queryParam("query", "${searchQuery}")
        .queryParam("limit", "50")
        .header("Authorization", "Bearer ${accessToken}")
        .check(status.in(200, 401, 429))
        .check(responseTimeInMillis.lte(3000))
    )

    val heavyTravelPlanOperation = exec(
      http("Heavy Travel Plan Request")
        .post(s"$apiVersion/travel-plans")
        .header("Authorization", "Bearer ${accessToken}")
        .body(StringBody("""{
          "title": "Spike Test Emergency Plan #{randomLong()}",
          "destination": "${searchQuery}",
          "startDate": "2024-06-01",
          "endDate": "2024-06-07",
          "budget": #{randomInt(2000000, 8000000)},
          "travelers": #{randomInt(4, 12)},
          "preferences": ["문화체험", "맛집투어", "자연관광", "쇼핑", "야경명소", "역사탐방", "레저활동"],
          "accommodation": "리조트",
          "transportation": "렌터카",
          "ageGroup": "전연령",
          "travelStyle": "바쁘게"
        }""")).asJson
        .check(status.in(201, 401, 429, 500, 503))
        .check(responseTimeInMillis.lte(10000))
    )

    val rapidHealthChecks = exec(
      http("Rapid Health Check")
        .get("/actuator/health")
        .check(status.in(200, 503))
        .check(responseTimeInMillis.lte(1000))
    )

    val popularPlacesHeavyLoad = exec(
      http("Popular Places Heavy Load")
        .get(s"$apiVersion/places/popular")
        .queryParam("category", "${category}")
        .queryParam("limit", "100")
        .queryParam("sort", "popularity")
        .check(status.in(200, 429, 500, 503))
        .check(responseTimeInMillis.lte(5000))
    )
  }

  // Baseline Traffic Scenario
  val baselineTraffic = scenario("Baseline Traffic")
    .feed(userCredentials)
    .exec(SpikeOperations.authenticateAndSearch)
    .pause(2, 5)
    .feed(searchQueries)
    .exec(SpikeOperations.popularPlacesHeavyLoad)
    .pause(3, 7)
    .exec(SpikeOperations.heavyTravelPlanOperation)
    .pause(1, 3)

  // Spike Traffic Scenario
  val spikeTraffic = scenario("Spike Traffic")
    .feed(userCredentials)
    .exec(SpikeOperations.authenticateAndSearch)
    .pause(100.milliseconds, 500.milliseconds)
    .feed(searchQueries)
    .repeat(2) {
      exec(SpikeOperations.popularPlacesHeavyLoad)
        .pause(100.milliseconds, 300.milliseconds)
    }
    .exec(SpikeOperations.heavyTravelPlanOperation)

  // Recovery Monitoring Scenario
  val recoveryMonitor = scenario("Recovery Monitor")
    .exec(SpikeOperations.rapidHealthChecks)
    .pause(1, 2)
    .exec(SpikeOperations.authenticateAndSearch)
    .pause(2, 4)

  // System Monitoring During Spike
  val systemMonitor = scenario("System Monitor")
    .exec(
      http("System Health During Spike")
        .get("/actuator/health")
        .check(status.in(200, 503))
        .check(responseTimeInMillis.lte(2000))
    )
    .exec(
      http("System Metrics")
        .get("/actuator/metrics")
        .check(status.in(200, 404, 503))
    )
    .pause(10)

  // Spike Test Pattern Setup
  setUp(
    // Phase 1: Baseline - Normal traffic
    baselineTraffic.inject(
      constantUsersPerSec(2).during(baselineDuration)
    ).andThen(
      // Phase 2: Spike - Sudden increase
      spikeTraffic.inject(
        rampUsers(spikeUsers).during(30.seconds),
        constantUsersPerSec(spikeUsers / 60).during(spikeDuration - 30.seconds)
      ).andThen(
        // Phase 3: Recovery - Return to baseline and monitor
        recoveryMonitor.inject(
          rampUsers(baselineUsers).during(1.minute),
          constantUsersPerSec(1).during(recoveryDuration - 1.minute)
        )
      )
    ),
    
    // Continuous system monitoring throughout all phases
    systemMonitor.inject(
      constantUsersPerSec(0.1).during(baselineDuration + spikeDuration + recoveryDuration)
    )
  ).protocols(httpProtocol)
    .maxDuration(baselineDuration + spikeDuration + recoveryDuration + 2.minutes)
    .assertions(
      // Overall system should remain stable
      global.responseTime.max.lt(15000),
      
      // Baseline performance should be good
      details("Auth + Search Combo").responseTime.percentile3.lt(2000),
      
      // During spike, we allow some degradation but not total failure
      global.successfulRequests.percent.gt(70), // Allow 30% failure during extreme spike
      
      // Health checks should mostly work
      details("System Health During Spike").successfulRequests.percent.gt(80),
      details("Rapid Health Check").successfulRequests.percent.gt(75),
      
      // Recovery phase should show improvement
      details("Recovery Monitor").responseTime.mean.lt(3000)
    )
    .before {
      println(s"""
        |========================================
        |  Oddiya Spike Test Configuration
        |========================================
        |  Base URL: $baseUrl
        |  
        |  Test Pattern:
        |  Phase 1 - Baseline: ${baselineUsers} users for ${baselineDuration}
        |  Phase 2 - Spike: ${spikeUsers} users for ${spikeDuration}
        |  Phase 3 - Recovery: ${baselineUsers} users for ${recoveryDuration}
        |  
        |  Monitoring: Continuous system health checks
        |  
        |  Goals:
        |  - Test auto-scaling response
        |  - Measure recovery time
        |  - Identify breaking points
        |  - Validate error handling
        |========================================
      """.stripMargin)
    }
    .after {
      println(s"""
        |========================================
        |  Spike Test Analysis Guidelines
        |========================================
        |  
        |  Key Metrics to Analyze:
        |  1. Response Time Spike Pattern
        |     - How quickly did response times increase?
        |     - What was the peak degradation?
        |     - How long did recovery take?
        |  
        |  2. Error Rate During Spike
        |     - What types of errors occurred?
        |     - Which endpoints were most affected?
        |     - Were errors graceful (429, 503) or crashes (500)?
        |  
        |  3. System Behavior
        |     - Did auto-scaling trigger?
        |     - How did the database perform?
        |     - Were circuit breakers activated?
        |  
        |  4. Recovery Characteristics
        |     - How long to return to baseline performance?
        |     - Were there any lingering effects?
        |     - Did the system stabilize properly?
        |  
        |  Expected Patterns:
        |  - Initial spike should show increased response times
        |  - Some 429 (rate limiting) or 503 (service unavailable) errors
        |  - Gradual improvement during recovery phase
        |  - Return to baseline performance within recovery period
        |========================================
      """.stripMargin)
    }
}