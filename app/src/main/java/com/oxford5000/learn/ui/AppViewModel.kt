package com.oxford5000.learn.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oxford5000.learn.data.Progress
import com.oxford5000.learn.data.ProgressStore
import com.oxford5000.learn.data.Word
import com.oxford5000.learn.data.WordRepository
import com.oxford5000.learn.data.levelKey
import com.oxford5000.learn.data.Lang
import com.oxford5000.learn.data.slot
import com.oxford5000.learn.data.PHRASE_STAGES
import com.oxford5000.learn.data.SET_STAGE
import com.oxford5000.learn.data.IDIOM_STAGE
import com.oxford5000.learn.logic.Direction
import com.oxford5000.learn.logic.LearningSession
import com.oxford5000.learn.logic.MarathonEngine
import com.oxford5000.learn.logic.MarathonState
import com.oxford5000.learn.logic.OptionPicker
import com.oxford5000.learn.logic.Question
import com.oxford5000.learn.logic.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

sealed interface Screen {
    data object Home : Screen
    data object Stages : Screen
    data class Levels(val stage: String) : Screen
    data class Preview(val stage: String, val level: Int) : Screen
    data class LearnQuiz(val stage: String, val level: Int) : Screen
    data class LearnResult(val stage: String, val level: Int, val passed: Boolean, val errors: Int, val wrong: List<Word>) : Screen
    data object Marathon : Screen
    data object MarathonQuiz : Screen
    data class MarathonResult(val total: Int, val firstRoundErrors: Int, val rounds: Int) : Screen
    data object PhraseGroups : Screen
    data object Errors : Screen
    data object ErrorsQuiz : Screen
    data class ErrorsResult(val total: Int, val removed: Int, val left: Int) : Screen
}

/** Всё, что нужно экрану вопроса, в одном снимке. */
data class QuizUi(
    val question: Question,
    val answered: Boolean,
    val selected: Int,
    val header: String,
    val progress: Float,
    /** Сколько «жизней» осталось (только в Обучении). */
    val livesLeft: Int?,
    /** Пояснение под шапкой (например, «уровень уже не пройден»). */
    val note: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = ProgressStore(app)
    private val rnd = Random.Default

    var repo by mutableStateOf<WordRepository?>(null); private set
    private var picker: OptionPicker? = null
    /** Режим «Выражения»: отдельный набор (устойчивые выражения + идиомы). */
    var phraseRepo by mutableStateOf<WordRepository?>(null); private set
    private var phrasePicker: OptionPicker? = null

    fun isPhraseStage(stage: String) = stage in PHRASE_STAGES
    fun repoFor(stage: String): WordRepository? = if (isPhraseStage(stage)) phraseRepo else repo
    private fun pickerFor(stage: String): OptionPicker? = if (isPhraseStage(stage)) phrasePicker else picker
    fun wordById(id: Int): Word? = repo?.byId?.get(id) ?: phraseRepo?.byId?.get(id)

    /** Название этапа для заголовков: A1…C2 как есть, группы выражений — словами. */
    fun stageLabel(stage: String): String = when (stage) {
        SET_STAGE -> s.setPhrases
        IDIOM_STAGE -> s.idioms
        else -> stage
    }

    var progress by mutableStateOf(Progress()); private set
    val dark: Boolean get() = progress.darkMode
    val lang: Lang get() = progress.lang
    /** Тексты интерфейса на текущем языке. */
    val s: S get() = strings(lang)
    /** Ключ набора прогресса для текущих языка и темы. */
    private val curSlot: String get() = slot(lang, dark)

    /** Показать окно-объяснение тёмного режима (при первом нажатии на переключатель). */
    var showDarkIntro by mutableStateOf(false); private set

    val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()

    var quiz by mutableStateOf<QuizUi?>(null); private set

    private var session: LearningSession? = null
    private var marathon: MarathonState? = null
    private var marathonQuestion: Question? = null
    private var marathonAnswered = false
    private var marathonSelected = -1
    private var autoNextJob: Job? = null

    init {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                getApplication<Application>().assets.open("words.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val r = withContext(Dispatchers.Default) { WordRepository.fromJson(text) }
            picker = OptionPicker(r.words)
            repo = r
            val ptext = withContext(Dispatchers.IO) {
                getApplication<Application>().assets.open("phrases.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val pr = withContext(Dispatchers.Default) { WordRepository.fromJson(ptext) }
            phrasePicker = OptionPicker(pr.words)
            phraseRepo = pr
        }
        viewModelScope.launch { store.progress.collect { progress = it } }
    }

    // ---------- навигация ----------

    fun push(s: Screen) { stack.add(s) }

    private fun replace(s: Screen) { stack[stack.lastIndex] = s }

    fun back() {
        autoNextJob?.cancel()
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (screen !is Screen.LearnQuiz && screen !is Screen.MarathonQuiz && screen !is Screen.ErrorsQuiz) quiz = null
    }

    /** Вернуться к ближайшему экрану нужного типа (или добавить его поверх главного). */
    private fun popTo(predicate: (Screen) -> Boolean, fallback: Screen) {
        autoNextJob?.cancel()
        quiz = null
        val i = stack.indexOfLast(predicate)
        if (i >= 0) { while (stack.size > i + 1) stack.removeAt(stack.lastIndex) }
        else { while (stack.size > 1) stack.removeAt(stack.lastIndex); stack.add(fallback) }
    }

    fun toggleDark() {
        if (!progress.darkIntroShown) { showDarkIntro = true; return }
        viewModelScope.launch { store.setDarkMode(!dark) }
    }

    /** «Дальше» в окне про тёмный режим: запоминаем, что окно показано, и переключаем тему. */
    fun confirmDarkIntro() {
        showDarkIntro = false
        val target = !dark
        viewModelScope.launch {
            store.setDarkIntroShown()
            store.setDarkMode(target)
        }
    }

    fun dismissDarkIntro() { showDarkIntro = false }

    fun setLang(l: Lang) { viewModelScope.launch { store.setLang(l) } }

    fun isPassed(stage: String, level: Int) = levelKey(stage, level) in progress.passed(dark)

    fun passedCount(stage: String): Int {
        val r = repoFor(stage) ?: return 0
        return (1..r.levelCount(stage)).count { isPassed(stage, it) }
    }

    // ---------- Обучение ----------

    fun startLevel(stage: String, level: Int) {
        val r = repoFor(stage) ?: return
        val p = pickerFor(stage) ?: return
        session = LearningSession(r.levelWords(stage, level), r.reviewPool(stage, level), dark, p, rnd, lang)
        val target = Screen.LearnQuiz(stage, level)
        if (screen is Screen.Preview || screen is Screen.LearnResult) replace(target) else push(target)
        refreshLearnUi()
    }

    private fun refreshLearnUi() {
        val s = session ?: return
        quiz = QuizUi(
            question = s.current,
            answered = s.answered,
            selected = s.selectedIndex,
            header = this.s.questionOf(s.index + 1, s.total),
            progress = (s.index + if (s.answered) 1 else 0).toFloat() / s.total,
            livesLeft = (LearningSession.MAX_ERRORS - s.errors).coerceAtLeast(0),
            note = if (s.doomed) this.s.levelFailedNote else null,
        )
    }

    private fun answerLearn(i: Int) {
        val s = session ?: return
        if (s.answered || s.status != SessionStatus.IN_PROGRESS) return
        val correct = s.answer(i)
        if (!correct) recordError(s.current.word.id)
        refreshLearnUi()
        if (correct) scheduleAutoNext()
    }

    private fun nextLearn() {
        val s = session ?: return
        val sc = screen as? Screen.LearnQuiz ?: return
        if (s.status == SessionStatus.IN_PROGRESS) s.next()
        when (s.status) {
            SessionStatus.IN_PROGRESS -> refreshLearnUi()
            SessionStatus.PASSED -> {
                val sl = curSlot
                viewModelScope.launch { store.markPassed(sl, levelKey(sc.stage, sc.level)) }
                finishLearn(sc, true, s)
            }
            SessionStatus.FAILED -> finishLearn(sc, false, s)
        }
    }

    private fun finishLearn(sc: Screen.LearnQuiz, passed: Boolean, s: LearningSession) {
        quiz = null
        session = null
        replace(Screen.LearnResult(sc.stage, sc.level, passed, s.errors, s.wrongWords.toList()))
    }

    fun openNextLevel(stage: String, level: Int) {
        val next = repoFor(stage)?.nextLevel(stage, level) ?: return
        if (isPhraseStage(stage)) popTo({ it is Screen.PhraseGroups }, Screen.PhraseGroups)
        else popTo({ it is Screen.Stages }, Screen.Stages)
        push(Screen.Levels(next.first))
        push(Screen.Preview(next.first, next.second))
    }

    fun backToLevels(stage: String) = popTo({ it is Screen.Levels && it.stage == stage }, Screen.Levels(stage))

    // ---------- Марафон ----------

    fun startMarathon(stages: List<String>) {
        val r = repo ?: return
        val st = MarathonEngine.start(stages, r.words, rnd)
        val sl = curSlot
        viewModelScope.launch { store.saveMarathon(sl, st) }
        openMarathon(st)
    }

    fun continueMarathon() {
        progress.marathon(dark)?.let { openMarathon(it) }
    }

    fun resetMarathon() {
        val sl = curSlot
        viewModelScope.launch { store.saveMarathon(sl, null) }
    }

    private fun openMarathon(st: MarathonState) {
        marathon = st
        buildMarathonQuestion()
        if (screen is Screen.MarathonResult) replace(Screen.MarathonQuiz) else push(Screen.MarathonQuiz)
        refreshMarathonUi()
    }

    private fun buildMarathonQuestion() {
        val st = marathon ?: return
        val w = st.currentId?.let { repo?.byId?.get(it) } ?: return
        val dir = if (dark && rnd.nextBoolean()) Direction.RU_EN else Direction.EN_RU
        marathonQuestion = picker!!.build(w, dir, rnd, lang)
        marathonAnswered = false
        marathonSelected = -1
    }

    private fun refreshMarathonUi() {
        val st = marathon ?: return
        val q = marathonQuestion ?: return
        val header = if (st.round == 1) s.questionOf(st.pos + 1, st.order.size)
        else s.reviewHeader(st.round, st.pos + 1, st.order.size)
        quiz = QuizUi(
            question = q, answered = marathonAnswered, selected = marathonSelected, header = header,
            progress = (st.pos + if (marathonAnswered) 1 else 0).toFloat() / st.order.size, livesLeft = null,
        )
    }

    /** Состояние, которое будет после «Дальше» (ответ уже засчитан и сохранён). */
    private var marathonAfter: MarathonState? = null

    private fun answerMarathon(i: Int) {
        val st = marathon ?: return
        val q = marathonQuestion ?: return
        if (marathonAnswered) return
        marathonAnswered = true
        marathonSelected = i
        val correct = i == q.correctIndex
        if (!correct) recordError(q.word.id)
        val newState = MarathonEngine.answer(st, correct, rnd)
        marathonAfter = newState
        val sl = curSlot
        viewModelScope.launch { store.saveMarathon(sl, newState) }
        refreshMarathonUi()
        if (correct) scheduleAutoNext()
    }

    private fun nextMarathon() {
        val after = marathonAfter ?: return
        marathonAfter = null
        marathon = after
        if (after.finished) {
            quiz = null
            marathon = null
            replace(Screen.MarathonResult(after.total, after.firstRoundErrors, after.round))
            return
        }
        buildMarathonQuestion()
        refreshMarathonUi()
    }

    // ---------- Ошибки ----------

    /** Слова, убранные в этом запуске (DataStore пишет асинхронно — чтобы они не всплыли в «Ещё круг»). */
    private val errRemovedIds = mutableMapOf<String, MutableSet<Int>>()

    private fun recordError(id: Int) {
        val sl = curSlot
        errRemovedIds[sl]?.remove(id)
        viewModelScope.launch { store.addError(sl, id) }
    }

    private var errOrder: List<Int> = emptyList()
    private var errPos = 0
    private var errRemoved = 0
    private var errQuestion: Question? = null
    private var errAnswered = false
    private var errSelected = -1

    fun startErrors() {
        val r = repo ?: return
        val removed = errRemovedIds[curSlot].orEmpty()
        errOrder = progress.errors(dark).filter { wordById(it) != null && it !in removed }.shuffled(rnd)
        if (errOrder.isEmpty()) return
        errPos = 0
        errRemoved = 0
        buildErrQuestion()
        if (screen is Screen.ErrorsResult) replace(Screen.ErrorsQuiz) else push(Screen.ErrorsQuiz)
        refreshErrUi()
    }

    private fun buildErrQuestion() {
        val w = wordById(errOrder[errPos]) ?: return
        val dir = if (dark && rnd.nextBoolean()) Direction.RU_EN else Direction.EN_RU
        errQuestion = pickerFor(w.stage)!!.build(w, dir, rnd, lang)
        errAnswered = false
        errSelected = -1
    }

    private fun refreshErrUi() {
        val q = errQuestion ?: return
        quiz = QuizUi(
            question = q, answered = errAnswered, selected = errSelected,
            header = s.errorsHeader(errPos + 1, errOrder.size, errRemoved),
            progress = (errPos + if (errAnswered) 1 else 0).toFloat() / errOrder.size, livesLeft = null,
        )
    }

    private fun answerErrors(i: Int) {
        val q = errQuestion ?: return
        if (errAnswered) return
        errAnswered = true
        errSelected = i
        val correct = i == q.correctIndex
        val sl = curSlot
        if (correct) {
            errRemoved++
            errRemovedIds.getOrPut(sl) { mutableSetOf() } += q.word.id
            viewModelScope.launch { store.removeError(sl, q.word.id) }
            scheduleAutoNext()
        }
        refreshErrUi()
    }

    private fun nextErrors() {
        if (!errAnswered) return
        if (errPos + 1 >= errOrder.size) {
            quiz = null
            replace(Screen.ErrorsResult(errOrder.size, errRemoved, errOrder.size - errRemoved))
            return
        }
        errPos++
        buildErrQuestion()
        refreshErrUi()
    }

    // ---------- общее для экрана вопроса ----------

    fun answer(i: Int) {
        when (screen) {
            is Screen.LearnQuiz -> answerLearn(i)
            is Screen.MarathonQuiz -> answerMarathon(i)
            is Screen.ErrorsQuiz -> answerErrors(i)
            else -> {}
        }
    }

    fun next() {
        autoNextJob?.cancel()
        when (screen) {
            is Screen.LearnQuiz -> nextLearn()
            is Screen.MarathonQuiz -> nextMarathon()
            is Screen.ErrorsQuiz -> nextErrors()
            else -> {}
        }
    }

    private fun scheduleAutoNext() {
        autoNextJob?.cancel()
        autoNextJob = viewModelScope.launch {
            delay(700)
            next()
        }
    }

    fun goHome() = popTo({ it is Screen.Home }, Screen.Home)

    fun openMarathonSetup() = popTo({ it is Screen.Marathon }, Screen.Marathon)

    fun openErrorsHome() = popTo({ it is Screen.Errors }, Screen.Errors)
}
