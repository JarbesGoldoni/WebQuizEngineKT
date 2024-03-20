package engine

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.persistence.*
import jakarta.validation.constraints.Email
import org.springframework.data.jpa.repository.JpaRepository
import jakarta.validation.constraints.*
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder


@Entity
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    val email: String = "",
    val password: String = "",
    @OneToMany(mappedBy = "user")
    val quizzes: Set<QuizComplete> = HashSet()
)

interface UserRepository: JpaRepository<User, Int> {
    fun existsByEmail(email: String): Boolean
}

@Entity
data class QuizComplete(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    val title: String = "",
    val text: String = "",
    @ElementCollection
    @CollectionTable(name = "QuizCompleteOptions", joinColumns = [JoinColumn(name = "id")])
    @Column(name = "option")
    val options: Array<String> = arrayOf(),
    @ElementCollection
    @CollectionTable(name = "QuizCompleteAnswers", joinColumns = [JoinColumn(name = "id")])
    @Column(name = "answer")
    val answers: Array<Int> = arrayOf(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User
)

interface QuizCompleteRepository: JpaRepository<QuizComplete, Int>


@SpringBootApplication
class WebQuizEngine {

    @RestController
    class Controller(
        private val quizRepository: QuizCompleteRepository,
        private val userRepository: UserRepository
    )
    {


        data class PostQuiz(@field:NotBlank val title: String, @field:NotBlank val text: String, @field:Size(min = 2) val options: Array<String>, val answer: Array<Int> = emptyArray())
        data class GetQuiz(val id: Int, val title: String, val text: String, val options: Array<String>)
        data class SolveRequest(val answer: Array<Int>)
        data class SolvedQuiz(val success: Boolean) {
            val feedback: String = if (success) "Congratulations, you're right!" else "Wrong answer! Please, try again."
        }
        data class RegisterData(@Email val email : String, @Size(min = 5) val password : String)


        @PostMapping("/api/quizzes")
        fun addQuiz(@Valid @RequestBody newQuiz: PostQuiz): ResponseEntity<Any> {
            val quiz = QuizComplete(
                title = newQuiz.title,
                text = newQuiz.text,
                options = newQuiz.options,
                answer = newQuiz.answer)
            val savedQuiz = quizRepository.save(quiz)
            return ResponseEntity(
                GetQuiz(savedQuiz.id, savedQuiz.title, savedQuiz.text, savedQuiz.options), HttpStatus.OK)
        }

        @PostMapping("/api/quizzes/{id}/solve")
        fun solveQuiz(@PathVariable id: Int, @RequestBody solverRequest: SolveRequest): ResponseEntity<Any> {
            val thisQuiz = quizRepository.findById(id).orElse(null)
            if (thisQuiz != null) {
                val sendBack = SolvedQuiz(thisQuiz.answer.contentEquals(solverRequest.answer))
                return ResponseEntity(
                    sendBack, HttpStatus.OK
                )
            } else {
                return ResponseEntity(HttpStatus.NOT_FOUND)
            }
        }

        @GetMapping("/api/quizzes/{id}")
        fun getQuizById(@PathVariable id: Int): ResponseEntity<Any> {
            val thisQuiz = quizRepository.findById(id).orElse(null)
            return if (thisQuiz != null) {
                ResponseEntity(
                    GetQuiz(thisQuiz.id, thisQuiz.title, thisQuiz.text, thisQuiz.options), HttpStatus.OK
                )
            } else {
                ResponseEntity(HttpStatus.NOT_FOUND)
            }
        }

        @GetMapping("/api/quizzes")
        fun getAllQuizzes(): ResponseEntity<Any> {
            return ResponseEntity(quizRepository.findAll(), HttpStatus.OK)
        }

        @PostMapping("/api/register")
        fun registerUser(@Valid @RequestBody register: RegisterData): ResponseEntity<Any> {
            return try {
                if (userRepository.existsByEmail(register.email)) {
                    throw IllegalStateException("Email already taken")
                }
                val encoder = BCryptPasswordEncoder()
                val hashedPassword = encoder.encode(register.password)
                val user = User(email = register.email, password = hashedPassword)
                userRepository.save(user)
                ResponseEntity(HttpStatus.OK)
            } catch (e: Exception) {
                ResponseEntity(HttpStatus.BAD_REQUEST)
            }
        }
    }

}



fun main(args: Array<String>) {
    runApplication<WebQuizEngine>(*args)
}
