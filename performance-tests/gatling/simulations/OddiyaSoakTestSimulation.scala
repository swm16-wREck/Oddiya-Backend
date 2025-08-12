import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Oddiya Soak Test Simulation
 * 
 * This simulation runs for an extended period (8 hours) to detect memory leaks,
 * resource accumulation, and long-term stability issues. It maintains consistent
 * load while monitoring for gradual performance degradation.
 * 
 * Duration: 8 hours (28800 seconds)
 * Load: 50 concurrent users with realistic usage patterns
 * Goal: Detect memory leaks and long-term stability issues
 */
class OddiyaSoakTestSimulation extends Simulation {

  // Configuration
  val baseUrl = System.getProperty("base.url", "http://localhost:8080")
  val apiVersion = "/api/v1"
  val soakUsers = Integer.getInteger("soak.users", 50)
  val soakDuration = Integer.getInteger("soak.duration", 480).toInt.minutes // 8 hours default
  val rampDuration = Integer.getInteger("ramp.duration", 10).toInt.minutes
  val warmupDuration = Integer.getInteger("warmup.duration", 30).toInt.minutes

  // HTTP Protocol Configuration
  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling-SoakTest/1.0")
    .shareConnections
    .connectionMaxLifetime(300.seconds)
    .disableCaching

  // Data Feeders
  val userCredentials = csv("user-credentials.csv").circular
  val searchQueries = csv("search-queries.csv").circular

  // Memory Leak Detection Variables
  var initialMemoryChecksDone = false
  var memoryCheckCount = 0

  // Soak Test Operations
  object SoakOperations {
    val authenticateWithMemoryCheck = exec(session => {
      memoryCheckCount += 1
      session.set("memoryCheckId", memoryCheckCount)
    }).exec(
      http("Soak Auth - Memory Check #{memoryCheckId}")
        .post(s"$apiVersion/auth/login")
        .body(StringBody("""{
          "provider": "google",
          "providerToken": "soak_test_#{randomLong()}_#{memoryCheckId}",
          "email": "soak_user_#{userId}@oddiya.com",
          "name": "Soak Test User #{userId}"
        }""")).asJson
        .check(status.is(200))
        .check(responseTimeInMillis.lte(2000))
        .check(jsonPath("$.data.accessToken").saveAs("accessToken"))
        .check(jsonPath("$.data.user.id").saveAs("userId"))
        .check(bodyBytes.saveAs("authResponseSize"))
    )

    val sustainedPlaceSearch = exec(
      http("Sustained Place Search")
        .get(s"$apiVersion/places/search")
        .queryParam("query", "${searchQuery}")
        .queryParam("limit", "30")
        .queryParam("radius", "10000")
        .header("Authorization", "Bearer ${accessToken}")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(500))
        .check(jsonPath("$.data[*].id").findAll.saveAs("placeIds"))
        .check(bodyBytes.saveAs("searchResponseSize"))
    )

    val longRunningTravelPlanCreation = exec(
      http("Long Running Travel Plan Creation")
        .post(s"$apiVersion/travel-plans")
        .header("Authorization", "Bearer ${accessToken}")
        .body(StringBody("""{
          "title": "Soak Test Long Plan #{__time()}_#{memoryCheckId}",
          "destination": "${searchQuery}",
          "startDate": "2024-06-01",
          "endDate": "2024-06-14",
          "budget": #{randomInt(2000000, 6000000)},
          "travelers": #{randomInt(2, 8)},
          "preferences": ["문화체험", "맛집투어", "자연관광", "쇼핑", "야경명소"],
          "accommodation": "호텔",
          "transportation": "대중교통",
          "ageGroup": "30대",
          "travelStyle": "여유롭게",
          "notes": "Soak test plan for memory leak detection - iteration #{memoryCheckId}"
        }""")).asJson
        .check(status.is(201))
        .check(responseTimeInMillis.lte(1000))
        .check(jsonPath("$.data.id").saveAs("travelPlanId"))
        .check(bodyBytes.saveAs("planResponseSize"))
    )

    val memoryIntensiveOperation = exec(
      http("Memory Intensive - Popular Places")
        .get(s"$apiVersion/places/popular")
        .queryParam("category", "${category}")
        .queryParam("limit", "100")
        .queryParam("sort", "rating")
        .queryParam("includeImages", "true")
        .queryParam("includeReviews", "true")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(1000))
        .check(bodyBytes.saveAs("popularResponseSize"))
    )

    val connectionPoolTest = exec(
      http("Connection Pool Test")
        .get(s"$apiVersion/travel-plans")
        .header("Authorization", "Bearer ${accessToken}")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(800))
    )

    val periodicHealthCheck = exec(
      http("Periodic Health Check")
        .get("/actuator/health")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(100))
        .check(jsonPath("$.status").is("UP"))
    ).exec(
      http("Memory Metrics Check")
        .get("/actuator/metrics/jvm.memory.used")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(200))
        .check(bodyBytes.saveAs("memoryMetricsSize"))
    )

    val sessionCleanup = exec(session => {
      // Log periodic memory information
      if (memoryCheckCount % 100 == 0) {
        println(s"Soak Test Progress - Iteration: ${memoryCheckCount}, Time: ${java.time.LocalTime.now()}")
      }
      session
    })
  }

  // Main Soak Test User Journey
  val soakTestUser = scenario("Soak Test User")
    .feed(userCredentials)
    .exec(SoakOperations.authenticateWithMemoryCheck)
    .pause(30, 60) // Realistic think time
    
    .repeat(9999, "soakIteration") { // Effectively infinite loop for soak duration
      feed(searchQueries)
        .exec(SoakOperations.sustainedPlaceSearch)
        .pause(15, 30)
        
        .exec(SoakOperations.memoryIntensiveOperation) 
        .pause(20, 40)
        
        .exec(SoakOperations.longRunningTravelPlanCreation)
        .pause(10, 25)
        
        .exec(SoakOperations.connectionPoolTest)
        .pause(5, 15)
        
        .exec(SoakOperations.sessionCleanup)
        .pause(45, 90) // Longer pause between iterations
    }

  // Memory Leak Detection Monitor
  val memoryMonitor = scenario("Memory Monitor")
    .exec(SoakOperations.periodicHealthCheck)
    .pause(2.minutes)
    .repeat(9999, "memoryCheck") {
      exec(
        http("Detailed Memory Check")
          .get("/actuator/metrics/jvm.memory.max")
          .check(status.is(200))
      )
      .exec(
        http("GC Activity Check")
          .get("/actuator/metrics/jvm.gc.pause")
          .check(status.is(200))
      )
      .exec(
        http("Thread Count Check")
          .get("/actuator/metrics/jvm.threads.live")
          .check(status.is(200))
      )
      .pause(5.minutes)
    }

  // Connection Pool Monitor
  val connectionMonitor = scenario("Connection Pool Monitor")
    .repeat(9999, "connectionCheck") {
      exec(
        http("Database Connection Pool")
          .get("/actuator/metrics/hikaricp.connections.active")
          .check(status.is(200))
      )
      .exec(
        http("HTTP Connection Pool")
          .get("/actuator/metrics/http.server.requests")
          .check(status.is(200))
      )
      .pause(3.minutes)
    }

  // Performance Baseline Monitor
  val baselineMonitor = scenario("Baseline Performance Monitor")
    .repeat(9999, "baselineCheck") {
      exec(
        http("Baseline Response Time Check")
          .get(s"$apiVersion/places/search")
          .queryParam("query", "서울")
          .queryParam("limit", "10")
          .check(status.is(200))
          .check(responseTimeInMillis.lte(300))
      )
      .pause(10.minutes)
    }

  // Soak Test Setup
  setUp(
    // Main soak test users
    soakTestUser.inject(
      rampUsers(soakUsers).during(rampDuration),
      constantUsersPerSec(soakUsers.toDouble / 3600).during(soakDuration) // Maintain steady state
    ),
    
    // Memory monitoring
    memoryMonitor.inject(
      nothingFor(warmupDuration),
      atOnceUsers(1)
    ),
    
    // Connection monitoring
    connectionMonitor.inject(
      nothingFor(warmupDuration),
      atOnceUsers(1)
    ),
    
    // Performance baseline monitoring
    baselineMonitor.inject(
      nothingFor(warmupDuration + 30.minutes),
      atOnceUsers(1)
    )
  ).protocols(httpProtocol)
    .maxDuration(warmupDuration + soakDuration + rampDuration)
    .assertions(
      // Soak test assertions are more focused on stability than performance
      global.responseTime.max.lt(10000), // Allow some degradation over time
      global.responseTime.mean.lt(2000),  // Mean should stay reasonable
      global.successfulRequests.percent.gt(99), // Very high success rate required
      
      // Memory leak detection through consistent performance
      details("Sustained Place Search").responseTime.percentile1.lt(500),
      details("Long Running Travel Plan Creation").responseTime.percentile2.lt(1000),
      details("Periodic Health Check").responseTime.max.lt(200),
      details("Periodic Health Check").successfulRequests.percent.gt(99),
      
      // Connection stability
      details("Connection Pool Test").successfulRequests.percent.gt(99),
      details("Database Connection Pool").successfulRequests.percent.gt(95),
      
      // Baseline performance should not degrade significantly
      details("Baseline Response Time Check").responseTime.percentile3.lt(400)
    )
    .before {
      println(s"""
        |========================================
        |  Oddiya Soak Test Configuration
        |========================================
        |  Base URL: $baseUrl
        |  Users: $soakUsers concurrent users
        |  Duration: $soakDuration (${soakDuration.toMinutes / 60.0} hours)
        |  Ramp Duration: $rampDuration
        |  Warmup Duration: $warmupDuration
        |  
        |  Monitoring:
        |  - Memory usage and GC activity
        |  - Connection pool utilization
        |  - Response time degradation
        |  - Resource accumulation
        |  
        |  Memory Leak Detection:
        |  - Consistent response times over duration
        |  - Stable memory usage patterns  
        |  - No resource accumulation
        |  - Healthy GC activity
        |  
        |  Expected Behavior:
        |  - Steady performance throughout test
        |  - Stable memory usage (no continuous growth)
        |  - Consistent response times
        |  - No connection pool exhaustion
        |========================================
      """.stripMargin)
    }
    .after {
      println(s"""
        |========================================
        |  Soak Test Analysis Guidelines
        |========================================
        |  
        |  Memory Leak Indicators:
        |  1. Continuously increasing response times
        |  2. Growing memory usage over time
        |  3. Increasing GC frequency/duration
        |  4. Connection pool exhaustion
        |  5. Thread count growth
        |  
        |  Healthy Patterns:
        |  1. Stable response times throughout test
        |  2. Consistent memory usage (sawtooth pattern from GC)
        |  3. Regular, efficient GC cycles
        |  4. Stable connection counts
        |  5. Consistent thread utilization
        |  
        |  Key Metrics to Analyze:
        |  - Response time trends over the ${soakDuration.toHours} hour period
        |  - Memory usage patterns and GC activity
        |  - Connection pool utilization
        |  - Error rates over time
        |  - Thread count stability
        |  
        |  If Issues Found:
        |  - Examine heap dumps at different time points
        |  - Analyze GC logs for pattern changes
        |  - Check for unclosed resources
        |  - Review connection management
        |  - Monitor for memory-intensive operations
        |========================================
      """.stripMargin)
    }
}