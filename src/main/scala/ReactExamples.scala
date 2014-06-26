package golly

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.{document, console, window}
import react.scalatags.ReactDom._
import react.scalatags.ReactDom.all._
import react._

import scala.collection.immutable.SortedSet
import scalaz.Lens

object ReactExamples {

  object Sample1 {

    case class HelloProps(name: String, age: Int)

    val component = ComponentBuilder[HelloProps, Unit]("sample1")
      .render(t =>
        div(backgroundColor := "#fdd", color := "#c00")(
          h1("THIS IS COOL."),
          p(textDecoration := "underline")("Hello there, ", "Hello, ", t.props.name, " of age ", t.props.age)
        ).render
      ).build

    def apply(): Unit = {
      React.renderComponent(component.create(HelloProps("Johnhy", 100)), document getElementById "target")
    }
  }
  
  // ===================================================================================================================

  object Sample2 {

    case class MyProps(title: String, startTime: Long)

    case class MyState(secondsElapsed: Long) {
      def inc = MyState(secondsElapsed + 1)
    }

    class MyBackend {
      var interval: js.UndefOr[Int] = js.undefined
      def start(tick: js.Function): Unit = interval = window.setInterval(tick, 1000)
      def stop(): Unit = interval foreach window.clearInterval
    }

    val component = ComponentBuilder[MyProps, MyState]("sample2")
      .backend(_ => new MyBackend)
      .render(ctx =>
        div(backgroundColor := "#fdd", color := "#c00")(
          h1("THIS IS AWESOME (", ctx.props.title, ")"),
          p(textDecoration := "underline")("Seconds elapsed: ", ctx.state.secondsElapsed)
        ).render
      )
      .getInitialState(ctx => MyState(ctx.props.startTime))
      .componentDidMount(ctx => {
        val tick: js.Function = (_: js.Any) => ctx.modState(_.inc)
        console log "Installing timer..."
        ctx.backend.start(tick)
      })
      .componentWillUnmount(_.backend.stop)
      .build

    def apply(): Unit = {
      React.renderComponent(component.create(MyProps("Great", 0)), document getElementById "target")
      React.renderComponent(component.create(MyProps("Again", 1000)), document getElementById "target2")
    }
  }

  // ===================================================================================================================

  object Sample3 {

    case class State(items: List[String], text: String)

    val inputRef = Ref[dom.HTMLInputElement]("i")

    val TodoList = ComponentBuilder[List[String], Unit]("TodoList")
      .render(t =>
        ul(t.props.map(itemText => li(itemText))).render
      ).build

    val TodoApp = ComponentBuilder[Unit, State]("TodoApp")
      .backend(new Backend(_))
      .render(t =>
        div(
          h3("TODO"),
          TodoList.create(t.state.items),
          form(onSubmit ==> t.backend.handleSubmit)(
            input(onChange ==> t.backend.onChange, value := t.state.text, ref := inputRef)(),
            button("Add #", t.state.items.length + 1)
          )
        ).render
      )
      .initialState(State(List("Sample todo #1", "Sample todo #2"), "Sample todo #3"))
      .build

    class Backend(t: ComponentScopeB[Unit, State]) {
      val handleSubmit: SyntheticEvent[dom.HTMLInputElement] => Unit = e => {
        e.preventDefault()
        val nextItems = t.state.items :+ t.state.text
        t.setState(State(nextItems, ""))
        inputRef(t).getDOMNode().focus()
      }

      val onChange: SyntheticEvent[dom.HTMLInputElement] => Unit = e =>
        t.modState(_.copy(text = e.target.value))
    }

    def apply(): Unit = {
      React.renderComponent(TodoApp.create(()), document getElementById "target")
    }

  }

  // ===================================================================================================================

  def textChangeRecv(f: String => Unit): SyntheticEvent[dom.HTMLInputElement] => Unit = e => f(e.target.value)
  def textChangeRecvL[State](t: ComponentScopeB[_, State], l: Lens[State, String]) = textChangeRecv(t.setL(l))

  object Sample4 {

    case class State(people: SortedSet[String], text: String)
    val stateTextL = Lens.lensg[State, String](a => b => a.copy(text = b), _.text)

    class PeopleListBackend(t: ComponentScopeB[Unit, State]) {
      def delete(name: String): Unit = {
        val p = t.state.people
        if (p.contains(name))
          t.setState(State(p - name, name))
      }

      val onChange = textChangeRecvL(t, stateTextL)

      val onKP: SyntheticEvent[dom.HTMLInputElement] => Unit =
        e => if (e.keyboardEvent.keyCode == 13) {
            e.preventDefault()
            add()
          }

      def add(): Unit = t.setState(State(t.state.people + t.state.text, ""))
    }

    case class PeopleListProps(people: SortedSet[String], deleteFn: String => Unit)

    val PeopleList = ComponentBuilder[PeopleListProps, Unit]("PeopleList")
      .render(t =>
        if (t.props.people.isEmpty)
          div(color := "#800")("No people in your list!!").render
        else
          ol(t.props.people.toList.map(p =>
            li(p, button(marginLeft := 1.em, onClick runs t.props.deleteFn(p))("Delete"))
          )).render
      )
      .build

    val PeopleEditor = ComponentBuilder[Unit, State]("PeopleEditor")
      .backend(new PeopleListBackend(_))
      .render(t =>
          div(
            h3("People List")
            ,div(PeopleList.create(PeopleListProps(t.state.people, t.backend.delete)))
            ,h3("Add")
            ,input(onChange ==> t.backend.onChange, onKeyPress ==> t.backend.onKP, value := t.state.text)()
            ,button(onClick runs t.backend.add())("+")
          ).render
      )
      .getInitialState(_ => State(SortedSet("First","Second"), "Middle"))
      .build

    def apply(): Unit =
      React.renderComponent(PeopleEditor.create(()), document getElementById "target2")
  }
}