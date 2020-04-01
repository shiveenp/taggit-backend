package main.kotlin.io.taggit

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import me.liuwj.ktorm.entity.Entity
import me.liuwj.ktorm.jackson.KtormModule
import me.liuwj.ktorm.jackson.json
import me.liuwj.ktorm.schema.BaseTable
import me.liuwj.ktorm.schema.SqlType
import me.liuwj.ktorm.schema.TypeReference
import org.postgresql.util.PGobject
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.*

fun <E : Entity<E>> BaseTable<E>.uuid(name: String): BaseTable<E>.ColumnRegistration<UUID> {
    return registerColumn(name, UuidSqlType)
}

object UuidSqlType : SqlType<UUID>(Types.OTHER, "uuid") {

    override fun doSetParameter(ps: PreparedStatement, index: Int, parameter: UUID) {
        ps.setObject(index, parameter)
    }

    override fun doGetResult(rs: ResultSet, index: Int): UUID {
        return rs.getObject(index) as UUID
    }
}

/**
 * A shared [ObjectMapper] instance which is used as the default mapper of [json] SQL type.
 */
val sharedObjectMapper: ObjectMapper = ObjectMapper().registerModules(KtormModule(), KotlinModule(), JavaTimeModule())

/**
 * Define a column typed of [JsonbSqlType].
 *
 * @param name the column's name.
 * @param typeRef the generic type infomation of this column, generally created by [me.liuwj.ktorm.schema.typeRef].
 * @param mapper the object mapper used to serialize column values to JSON strings and deserialize them.
 * @return the column registration that wraps the registered column.
 */
fun <E : Any, C : Any> BaseTable<E>.jsonb(
    name: String,
    typeRef: TypeReference<C>,
    mapper: ObjectMapper = sharedObjectMapper
): BaseTable<E>.ColumnRegistration<C> {
    return registerColumn(name, JsonbSqlType(mapper, mapper.constructType(typeRef.referencedType)))
}

/**
 * [SqlType] implementation that provides JSON data type support via Jackson framework.
 *
 * @property objectMapper the object mapper used to serialize column values to JSON strings and deserialize them.
 * @property javaType the generic type information represented as Jackson's [JavaType].
 */
class JsonbSqlType<T : Any>(
    val objectMapper: ObjectMapper,
    val javaType: JavaType
) : SqlType<T>(Types.JAVA_OBJECT, "jsonb") {

    val pgObject = PGobject()

    override fun doSetParameter(ps: PreparedStatement, index: Int, parameter: T) {
        pgObject.type = "json"
        pgObject.value = objectMapper.writeValueAsString(parameter)
        ps.setObject(index, pgObject)
    }

    override fun doGetResult(rs: ResultSet, index: Int): T? {
        val json = rs.getString(index)
        if (json.isNullOrBlank()) {
            return null
        } else {
            return objectMapper.readValue(json, javaType)
        }
    }
}
