import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Oddiya Stress Test Simulation
 * 
 * This simulation applies stress testing to identify system breaking points.
 * It tests with 100, 500, and 1000 concurrent users to understand how the system
 * behaves under increasing load and identifies performance degradation points.
 * 
 * Targets: 100/500/1000 users with minimal think time
 * Goal: Find breaking point and measure graceful degradation
 */
class OddiyaStressTestSimulation extends Simulation {

  // Configuration
  val baseUrl = System.getProperty("base.url", "http://localhost:8080")
  val apiVersion = "/api/v1"
  val stressLevel = Integer.getInteger("stress.level", 1)
  val testDuration = Integer.getInteger("duration", 15).toInt.minutes
  val rampDuration = Integer.getInteger("ramp", 5).toInt.minutes

  // HTTP Protocol Configuration
  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling-StressTest/1.0")
    .shareConnections
    .maxConnectionsPerHost(1000)
    .disableCaching

  // Data Feeders
  val userCredentials = csv("user-credentials.csv").circular
  val searchQueries = csv("search-queries.csv").circular

  // Stress Test Scenarios
  object StressOperations {
    val heavySearch = exec(
      http("Heavy Search Operation")
        .get(s"$apiVersion/places/search")
        .queryParam("query", "${searchQuery}")
        .queryParam("limit", "#{randomInt(100, 500)}")
        .queryParam("radius", "#{randomInt(10000, 100000)}")
        .check(status.in(200, 429, 500, 503))
        .check(responseTimeInMillis.lte(10000))
    )

    val intensiveTravelPlanCreation = exec(
      http("Intensive Travel Plan Creation")
        .post(s"$apiVersion/travel-plans")
        .header("Authorization", "Bearer ${accessToken}")
        .body(StringBody("""{
          "title": "Stress Test Complex Plan #{randomLong()}",
          "destination": "${searchQuery}",
          "startDate": "2024-06-01",
          "endDate": "2024-06-30",
          "budget": #{randomInt(1000000, 5000000)},
          "travelers": #{randomInt(2, 10)},
          "preferences": ["문화체험", "맛집투어", "자연관광", "쇼핑", "야경명소", "역사탐방"],
          "accommodation": "호텔",
          "transportation": "렌터카",
          "ageGroup": "30대",
          "travelStyle": "모험적"
        }""")).asJson
        .check(status.in(201, 429, 500, 503))
        .check(responseTimeInMillis.lte(15000))
    )

    val databaseHeavyQuery = exec(
      http("Database Heavy Query")
        .get(s"$apiVersion/places/popular")
        .queryParam("category", "${category}")
        .queryParam("limit", "#{randomInt(200, 1000)}")
        .queryParam("sort", "rating")
        .queryParam("region", "전국")
        .check(status.in(200, 429, 500, 503))
        .check(responseTimeInMillis.lte(20000))
    )

    val rapidFireAuth = exec(
      http("Rapid Fire Auth")
        .post(s"$apiVersion/auth/login")
        .body(StringBody("""{
          "provider": "google",
          "providerToken": "stress_token_#{randomLong()}",
          "email": "stress_#{randomInt(1, 10000)}@oddiya.com",
          "name": "Stress User #{randomInt(1, 10000)}"
        }""")).asJson
        .check(status.in(200, 429, 500, 503))
        .check(responseTimeInMillis.lte(5000))
    )
  }

  // Stress Level 1: 100 Users
  val stressLevel1 = scenario("Stress Level 1 - 100 Users")
    .feed(userCredentials)
    .exec(StressOperations.rapidFireAuth)
    .pause(100.milliseconds, 500.milliseconds)
    .feed(searchQueries)
    .exec(StressOperations.heavySearch)
    .pause(100.milliseconds, 300.milliseconds)
    .exec(StressOperations.intensiveTravelPlanCreation)
    .pause(100.milliseconds, 200.milliseconds)
    .exec(StressOperations.databaseHeavyQuery)

  // Stress Level 2: 500 Users  
  val stressLevel2 = scenario("Stress Level 2 - 500 Users")
    .feed(userCredentials)
    .exec(StressOperations.rapidFireAuth)
    .pause(50.milliseconds, 200.milliseconds)
    .feed(searchQueries)
    .exec(StressOperations.heavySearch)
    .pause(50.milliseconds, 150.milliseconds)
    .repeat(2) {
      exec(StressOperations.databaseHeavyQuery)
        .pause(50.milliseconds, 100.milliseconds)
    }

  // Stress Level 3: 1000 Users
  val stressLevel3 = scenario("Stress Level 3 - 1000 Users")
    .feed(userCredentials)
    .exec(StressOperations.rapidFireAuth)
    .pause(25.milliseconds, 100.milliseconds)
    .feed(searchQueries)
    .repeat(3) {
      exec(StressOperations.heavySearch)
        .pause(25.milliseconds, 75.milliseconds)
    }

  // Health monitoring during stress
  val stressHealthMonitor = scenario("Stress Health Monitor")
    .exec(
      http("Health Check Under Stress")
        .get("/actuator/health")
        .check(status.in(200, 503))
        .check(responseTimeInMillis.lte(2000))
    )
    .pause(5)

  // Dynamic setup based on stress level
  val stressSetup = stressLevel match {
    case 1 => List(
      stressLevel1.inject(
        rampUsers(100).during(rampDuration),
        constantUsersPerSec(20).during(testDuration)
      ),
      stressHealthMonitor.inject(
        constantUsersPerSec(0.2).during(testDuration + rampDuration)
      )
    )
    
    case 2 => List(
      stressLevel2.inject(
        rampUsers(500).during(rampDuration),
        constantUsersPerSec(100).during(testDuration)
      ),
      stressHealthMonitor.inject(
        constantUsersPerSec(0.5).during(testDuration + rampDuration)
      )
    )
    
    case 3 => List(
      stressLevel3.inject(
        rampUsers(1000).during(rampDuration),
        constantUsersPerSec(200).during(testDuration)
      ),
      stressHealthMonitor.inject(
        constantUsersPerSec(1).during(testDuration + rampDuration)
      )
    )
    
    case _ => List(
      // Run all levels sequentially
      stressLevel1.inject(
        rampUsers(100).during(2.minutes),
        constantUsersPerSec(20).during(5.minutes)
      ),
      stressLevel2.inject(
        nothingFor(7.minutes),
        rampUsers(500).during(2.minutes),
        constantUsersPerSec(100).during(5.minutes)
      ),
      stressLevel3.inject(
        nothingFor(14.minutes),
        rampUsers(1000).during(2.minutes),
        constantUsersPerSec(200).during(5.minutes)
      ),
      stressHealthMonitor.inject(
        constantUsersPerSec(1).during(21.minutes)
      )
    )
  }

  setUp(stressSetup: _*)
    .protocols(httpProtocol)
    .maxDuration(testDuration + rampDuration + 2.minutes)
    .assertions(
      // Stress testing focuses on system behavior under load
      // We expect some degradation, so assertions are more lenient
      global.responseTime.max.lt(30000), // Allow up to 30s for extreme conditions
      global.successfulRequests.percent.gt(85), // Allow 15% failure under stress
      
      // Monitor critical endpoints
      details("Health Check Under Stress").successfulRequests.percent.gt(80),
      details("Rapid Fire Auth").responseTime.percentile3.lt(10000),
      details("Heavy Search Operation").responseTime.percentile3.lt(20000)
    )
    .before {
      val userCount = stressLevel match {
        case 1 => "100"
        case 2 => "500" 
        case 3 => "1000"
        case _ => "100/500/1000 (Sequential)"
      }
      
      println(s"""
        |========================================
        |  Oddiya Stress Test Configuration
        |========================================
        |  Base URL: $baseUrl
        |  Stress Level: $stressLevel
        |  Users: $userCount
        |  Duration: $testDuration
        |  Ramp Duration: $rampDuration
        |  Think Time: Minimal (25-500ms)
        |  Goal: Find breaking points
        |========================================
      """.stripMargin)
    }
    .after {
      println(s"""
        |========================================
        |  Stress Test Completed
        |========================================
        |  Check the results for:
        |  - Response time degradation patterns
        |  - Error rate increases under load
        |  - System recovery behavior
        |  - Resource utilization peaks
        |========================================
      """.stripMargin)
    }
}