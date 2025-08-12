import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Oddiya Load Test Simulation
 * 
 * This simulation tests the Oddiya travel planning application under normal load conditions.
 * It simulates realistic user behavior with proper think times and validates response times
 * against SLA targets.
 * 
 * Target: 100 concurrent users for 30 minutes
 * SLA: Authentication <100ms, Search <200ms, Travel Plans <300ms
 */
class OddiyaLoadTestSimulation extends Simulation {

  // Configuration
  val baseUrl = System.getProperty("base.url", "http://localhost:8080")
  val apiVersion = "/api/v1"
  val users = Integer.getInteger("users", 100).toInt
  val duration = Integer.getInteger("duration", 30).toInt.minutes
  val rampDuration = Integer.getInteger("ramp", 5).toInt.minutes

  // HTTP Protocol Configuration
  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling-LoadTest/1.0")
    .shareConnections

  // Data Feeders
  val userCredentials = csv("user-credentials.csv").random
  val searchQueries = csv("search-queries.csv").random

  // Scenario Components
  object Authentication {
    val login = exec(
      http("POST /auth/login")
        .post(s"$apiVersion/auth/login")
        .body(StringBody("""{
          "provider": "google",
          "providerToken": "load_test_token_${email}",
          "email": "${email}",
          "name": "Load Test User"
        }""")).asJson
        .check(status.is(200))
        .check(responseTimeInMillis.lte(100))
        .check(jsonPath("$.data.accessToken").saveAs("accessToken"))
        .check(jsonPath("$.success").is("true"))
    )
  }

  object PlaceSearch {
    val searchPlaces = exec(
      http("GET /places/search")
        .get(s"$apiVersion/places/search")
        .queryParam("query", "${searchQuery}")
        .queryParam("limit", "20")
        .queryParam("radius", "5000")
        .header("Authorization", "Bearer ${accessToken}")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(200))
        .check(jsonPath("$.data").exists)
        .check(jsonPath("$.data[*].id").findAll.saveAs("placeIds"))
    )

    val getPlaceDetails = exec(
      http("GET /places/{id}")
        .get(s"$apiVersion/places/" + "${placeIds.random()}")
        .header("Authorization", "Bearer ${accessToken}")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(150))
        .check(jsonPath("$.data.id").exists)
    )

    val getPopularPlaces = exec(
      http("GET /places/popular")
        .get(s"$apiVersion/places/popular")
        .queryParam("category", "${category}")
        .queryParam("limit", "10")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(200))
        .check(jsonPath("$.data").exists)
    )
  }

  object TravelPlans {
    val createTravelPlan = exec(
      http("POST /travel-plans")
        .post(s"$apiVersion/travel-plans")
        .header("Authorization", "Bearer ${accessToken}")
        .body(StringBody("""{
          "title": "Load Test Plan ${__time()}",
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

    val getTravelPlans = exec(
      http("GET /travel-plans")
        .get(s"$apiVersion/travel-plans")
        .header("Authorization", "Bearer ${accessToken}")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(200))
        .check(jsonPath("$.data").exists)
    )

    val getTravelPlanDetails = exec(
      http("GET /travel-plans/{id}")
        .get(s"$apiVersion/travel-plans/" + "${travelPlanId}")
        .header("Authorization", "Bearer ${accessToken}")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(200))
        .check(jsonPath("$.data.id").exists)
    )
  }

  object HealthCheck {
    val healthCheck = exec(
      http("GET /actuator/health")
        .get("/actuator/health")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(50))
        .check(jsonPath("$.status").is("UP"))
    )
  }

  // User Scenarios
  val regularUser = scenario("Regular User Journey")
    .feed(userCredentials)
    .exec(Authentication.login)
    .pause(2, 5)
    .feed(searchQueries)
    .exec(PlaceSearch.searchPlaces)
    .pause(3, 8)
    .exec(PlaceSearch.getPlaceDetails)
    .pause(2, 4)
    .exec(TravelPlans.createTravelPlan)
    .pause(1, 3)
    .exec(TravelPlans.getTravelPlanDetails)
    .pause(2, 5)
    .exec(TravelPlans.getTravelPlans)

  val powerUser = scenario("Power User Journey")
    .feed(userCredentials)
    .exec(Authentication.login)
    .pause(1, 2)
    .repeat(3, "searchIteration") {
      feed(searchQueries)
        .exec(PlaceSearch.searchPlaces)
        .pause(1, 2)
        .exec(PlaceSearch.getPopularPlaces)
        .pause(1, 2)
    }
    .exec(TravelPlans.createTravelPlan)
    .pause(1, 2)
    .exec(TravelPlans.getTravelPlanDetails)
    .pause(1, 2)
    .exec(TravelPlans.getTravelPlans)

  val healthMonitor = scenario("Health Monitor")
    .exec(HealthCheck.healthCheck)
    .pause(30)

  // Load Test Setup
  setUp(
    regularUser.inject(
      rampUsers(users * 70 / 100).during(rampDuration)
    ),
    powerUser.inject(
      rampUsers(users * 25 / 100).during(rampDuration)
    ),
    healthMonitor.inject(
      constantUsersPerSec(1).during(duration)
    )
  ).protocols(httpProtocol)
    .maxDuration(duration + rampDuration)
    .assertions(
      // Global assertions
      global.responseTime.max.lt(5000),
      global.responseTime.mean.lt(1000),
      global.successfulRequests.percent.gt(99),
      
      // Authentication assertions
      details("POST /auth/login").responseTime.percentile3.lt(100),
      details("POST /auth/login").successfulRequests.percent.gt(99),
      
      // Search assertions
      details("GET /places/search").responseTime.percentile3.lt(200),
      details("GET /places/search").successfulRequests.percent.gt(98),
      
      // Travel plan assertions
      details("POST /travel-plans").responseTime.percentile3.lt(300),
      details("POST /travel-plans").successfulRequests.percent.gt(97),
      
      // Health check assertions
      details("GET /actuator/health").responseTime.max.lt(50),
      details("GET /actuator/health").successfulRequests.percent.gt(99)
    )
    .before {
      println(s"""
        |========================================
        |  Oddiya Load Test Configuration
        |========================================
        |  Base URL: $baseUrl
        |  Users: $users
        |  Duration: $duration
        |  Ramp Duration: $rampDuration
        |  Regular Users: ${users * 70 / 100}
        |  Power Users: ${users * 25 / 100}
        |  Health Monitors: 1 per 30s
        |========================================
      """.stripMargin)
    }
}