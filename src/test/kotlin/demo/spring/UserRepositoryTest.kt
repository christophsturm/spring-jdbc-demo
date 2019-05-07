package demo.spring

import org.flywaydb.core.Flyway
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.annotation.Id
import org.springframework.data.convert.CustomConversions
import org.springframework.data.jdbc.core.DefaultDataAccessStrategy
import org.springframework.data.jdbc.core.SqlGeneratorSource
import org.springframework.data.jdbc.repository.support.JdbcRepositoryFactory
import org.springframework.data.relational.core.conversion.BasicRelationalConverter
import org.springframework.data.relational.core.mapping.NamingStrategy
import org.springframework.data.relational.core.mapping.RelationalMappingContext
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.core.support.RepositoryFactorySupport
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import strikt.api.expectThat
import strikt.assertions.isEqualTo

data class UserPK(val id: Int)

data class User(@Id val userPK: UserPK?, val withingsAuth: String)

class UserRepositoryTest {
    @Test
    fun test() {
        val dataSource = JdbcDataSource()
        dataSource.setURL("jdbc:h2:mem:spring_jdbc_test;DB_CLOSE_DELAY=-1")
        dataSource.user = "sa"
        dataSource.password = "sa"
        val flyway = Flyway.configure().dataSource(dataSource).load()
        flyway.migrate()


        val relationalMappingContext = RelationalMappingContext(NamingStrategy.INSTANCE)
        val relationalConverter = BasicRelationalConverter(
            relationalMappingContext,
            CustomConversions(CustomConversions.StoreConversions.NONE, listOf(IdConverter(), PKConverter()))
        )
        val applicationContext = AnnotationConfigApplicationContext(MyConfig::class.java)

        val jdbcTemplate = NamedParameterJdbcTemplate(dataSource)
        val jdbcRepositoryFactory = JdbcRepositoryFactory(
            DefaultDataAccessStrategy(
                SqlGeneratorSource(
                    relationalMappingContext
                ), relationalMappingContext, relationalConverter, jdbcTemplate
            ), relationalMappingContext, relationalConverter, applicationContext, jdbcTemplate
        )
        val factory: RepositoryFactorySupport = jdbcRepositoryFactory
        val userRepository = factory.getRepository(UserRepository::class.java)
        val originalUser = User(null, "blah")
        val savedUser = userRepository.save(originalUser)
        expectThat(originalUser).get { withingsAuth }.isEqualTo(savedUser.withingsAuth)

// the next line fails because spring-data-jdbc wants to load the userPk
//        val loadedUser = userRepository.findByIdOrNull(savedUser.userPK!!)!!
//        expectThat(loadedUser).isEqualTo(savedUser)

    }
}

@Configuration
open class MyConfig

class IdConverter : Converter<Int, UserPK> {
    override fun convert(source: Int): UserPK {
        return UserPK(source)
    }
}

class PKConverter : Converter<UserPK, Int> {
    override fun convert(source: UserPK): Int {
        return source.id
    }
}

interface UserRepository : CrudRepository<User, UserPK>
