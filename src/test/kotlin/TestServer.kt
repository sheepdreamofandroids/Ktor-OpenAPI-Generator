import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.papsign.ktor.openapigen.APITag
import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.Path
import com.papsign.ktor.openapigen.annotations.Request
import com.papsign.ktor.openapigen.annotations.Response
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.interop.withAPI
import com.papsign.ktor.openapigen.openAPIGen
import com.papsign.ktor.openapigen.openapi.Described
import com.papsign.ktor.openapigen.openapi.Server
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.get
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import com.papsign.ktor.openapigen.route.tag
import io.ktor.application.application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.features.origin
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.host
import io.ktor.request.port
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

object TestServer {

    data class Error(val id: String, val msg: String)

    class ProperException(msg: String, val id: String = "proper.exception") : Exception(msg)

    @JvmStatic
    fun main(args: Array<String>) {
        embeddedServer(Netty, 8080, "localhost") {
            //define basic OpenAPI info
            val api = install(OpenAPIGen) {
                info {
                    version = "0.1"
                    title = "Test API"
                    description = "The Test API"
                    contact {
                        name = "Support"
                        email = "support@test.com"
                    }
                }
                server("https://api.test.com/") {
                    description = "Main production server"
                }
                schemaNamer = {
                    //rename DTOs from java type name to generator compatible form
                    val regex = Regex("[A-Za-z0-9_.]+")
                    it.toString().replace(regex) { it.value.split(".").last() }.replace(Regex(">|<|, "), "_")
                }
            }

            install(ContentNegotiation) {
                jackson {
                    enable(
                            DeserializationFeature.WRAP_EXCEPTIONS,
                            DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,
                            DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS
                    )

                    enable(SerializationFeature.WRAP_EXCEPTIONS, SerializationFeature.INDENT_OUTPUT)

                    setSerializationInclusion(JsonInclude.Include.NON_NULL)

                    setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
                        indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
                        indentObjectsWith(DefaultIndenter("  ", "\n"))
                    })

                    registerModule(JavaTimeModule())
                }
            }

            // StatusPage interop, can also define exceptions per-route
            install(StatusPages) {
                withAPI(api) {
                    exception<JsonMappingException, Error>(HttpStatusCode.BadRequest) {
                        it.printStackTrace()
                        Error("mapping.json", it.localizedMessage)
                    }
                    exception<ProperException, Error>(HttpStatusCode.BadRequest) {
                        it.printStackTrace()
                        Error(it.id, it.localizedMessage)
                    }
                }
            }


            val scopes = Scopes.values().asList()

            // serve OpenAPI and redirect from root
            routing {
                get("/openapi.json") {
                    val host = Server(
                            call.request.origin.scheme + "://" + call.request.host() + if (setOf(
                                            80,
                                            443
                                    ).contains(call.request.port())
                            ) "" else ":${call.request.port()}"
                    )
                    application.openAPIGen.api.servers.add(0, host)
                    call.respond(application.openAPIGen.api)
                    application.openAPIGen.api.servers.remove(host)
                }

                get("/") {
                    call.respondRedirect("/swagger-ui/index.html?url=/openapi.json", true)
                }
            }

            apiRouting {

                get<StringParam, StringResponse>(
                        info("String Param Endpoint", "This is a String Param Endpoint"),
                        example = StringResponse("Hi")
                ) { params ->
                    respond(StringResponse(params.a))
                }

                route("list") {
                    get<StringParam, List<StringResponse>>(
                        info("String Param Endpoint", "This is a String Param Endpoint"),
                        example = listOf(StringResponse("Hi"))
                    ) { params ->
                        respond(listOf(StringResponse(params.a)))
                    }
                }

                route("sealed") {
                    post<Unit, Base, Base>(
                            info("Sealed class Endpoint", "This is a Sealed class Endpoint"),
                            exampleRequest = Base.A("Hi"),
                            exampleResponse = Base.A("Hi")
                    ) { params, base ->
                        respond(base)
                    }
                }

                route("long").get<LongParam, LongResponse>(
                        info("Long Param Endpoint", "This is a String Param Endpoint"),
                        example = LongResponse(Long.MAX_VALUE)
                ) { params ->
                    respond(LongResponse(params.a))
                }

                route("again") {
                    tag(TestServer.Tags.EXAMPLE) {

                        get<StringParam, StringResponse>(
                                info("String Param Endpoint", "This is a String Param Endpoint"),
                                example = StringResponse("Hi")
                        ) { params ->
                            respond(StringResponse(params.a))
                        }

                        route("long").get<LongParam, LongResponse>(
                                info("Long Param Endpoint", "This is a String Param Endpoint"),
                                example = LongResponse(Long.MAX_VALUE)
                        ) { params ->
                            respond(LongResponse(params.a))
                        }
                    }


                }
            }
        }.start(true)
    }

    @Path("string/{a}")
    data class StringParam(@PathParam("A simple String Param") val a: String)

    @Response("A String Response")
    data class StringResponse(val str: String)


    @Response("A String Response")
    @Request("A String Request")
    data class StringUsable(val str: String)

    @Path("{a}")
    data class LongParam(@PathParam("A simple Long Param") val a: Long)

    @Response("A Long Response")
    data class LongResponse(val str: Long)

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes(
            JsonSubTypes.Type(Base.A::class, name = "a"),
            JsonSubTypes.Type(Base.B::class, name = "b"),
            JsonSubTypes.Type(Base.C::class, name = "c")
    )
    sealed class Base {
        class A(val str: String) : Base()
        class B(val i: Int) : Base()
        class C(val l: Long) : Base()
    }


    enum class Tags(override val description: String) : APITag {
        EXAMPLE("Wow this is a tag?!")
    }

    enum class Scopes(override val description: String) : Described {
        profile("Basic Profile scope"), email("Email scope")
    }

    data class APIPrincipal(val a: String, val b: String)
}
