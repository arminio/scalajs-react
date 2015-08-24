package japgolly.scalajs.react.extra

import org.scalajs.dom
import scala.concurrent.duration._
import scalajs.js
import japgolly.scalajs.react._, vdom.prefix_<^._
import dom.{document, window}
import dom.html.Element
import dom.raw.{CSSStyleDeclaration, Node}
import ReusabilityOverlay.Comp

/**
 * Heavily inspired by https://github.com/redsunsoft/react-render-visualizer
 */
object ReusabilityOverlay {
  type Comp = ComponentScope.Mounted[TopNode]

  private val key = "reusabilityOverlay"

  def install[P: Reusability, S: Reusability, B, N <: TopNode](newOverlay: Comp => ReusabilityOverlay) = {

    // Store the overlay stats on each instance
    def get(c: Comp): ReusabilityOverlay = {
      def $ = c.asInstanceOf[js.Dynamic]
      $.selectDynamic(key).asInstanceOf[js.UndefOr[WrapObj[ReusabilityOverlay]]].fold {
        val o = newOverlay(c)
        $.updateDynamic(key)(WrapObj(o))
        o
      }(_.v)
    }

    Reusability.shouldComponentUpdateAnd[P, S, B, N] { ($, p2, p, s2, s) =>
      val overlay = get($)
      if (p || s) {
        def fmt(update: Boolean, name: String, a: Any, b: Any) =
          if (!update)
            ""
          else if (a.toString.length < 50)
            s"$name update: [$a] ⇒ [$b]."
          else
            s"$name update:\n  [$a] ⇒\n  [$b]."
        val sep = if (p && s) "\n" else ""
        val reason = fmt(p, "Prop", $.props, p2) + sep + fmt(s, "State", $.state, s2)
        overlay logBad reason
      }
      else
        overlay.logGood
    } andThen (_
      .componentDidMount(get(_).onMount)
      .componentWillUnmount(get(_).onUnmount)
    )
  }
}

trait ReusabilityOverlay {
  def onMount  : Callback
  def onUnmount: Callback
  val logGood  : Callback
  def logBad(reason: String): Callback
}

// =====================================================================================================================

object DefaultReusabilityOverlay {

  lazy val defaults = Options(
    template             = ShowGoodAndBadCounts,
    reasonsToShowOnClick = 10,
    updatePositionEvery  = 500 millis,
    dynamicStyle         = (_,_,_) => (),
    mountHighlighter     = defaultHighlighter,
    updateHighlighter    = defaultHighlighter)

  case class Options(template            : Template,
                     reasonsToShowOnClick: Int,
                     updatePositionEvery : FiniteDuration,
                     dynamicStyle        : (Int, Int, Nodes) => Unit,
                     mountHighlighter    : Comp => Callback,
                     updateHighlighter   : Comp => Callback)

  @inline implicit def autoLiftHtml(n: Node) = n.asInstanceOf[Element]

  trait Template {
    def template: ReactElement
    def good(e: Element): Node
    def bad(e: Element): Node
  }

  val styleAll: TagMod =
    (^.fontSize := "0.9em") compose (^.lineHeight := "0.9em")

  val defaultContainer =
    <.div(
      ^.background   := "rgba(248,248,248,.83)",
      ^.padding      := "1px 2px",
      ^.border       := "solid 1px black",
      ^.borderRadius := "4px",
      ^.position     := "absolute",
      ^.zIndex       := "10000",
      ^.color        := "#444",
      ^.fontWeight   := "normal",
      ^.boxShadow    := "0 2px 6px rgba(0,0,0,0.25)",
      ^.cursor       := "pointer")

  object ShowGoodAndBadCounts extends Template {
    override val template: ReactElement =
      defaultContainer(styleAll,
        <.span(styleAll, ^.color := "#070"),
        <.span(styleAll, ^.padding := "0", ^.margin := "0 0.4ex", "-"),
        <.span(styleAll, ^.color := "#900", ^.fontWeight := "bold"))
    override def good(e: Element) = e childNodes 0
    override def bad (e: Element) = e childNodes 2
  }

  object ShowBadCount extends Template {
    override val template: ReactElement =
      defaultContainer(styleAll,
        <.span(styleAll, ^.display := "none"),
        <.span(styleAll, ^.color := "#900", ^.fontWeight := "bold"))
    override def good(e: Element) = e childNodes 0
    override def bad (e: Element) = e childNodes 1
  }

  def highlighter(before: CSSStyleDeclaration => Unit,
                  frame1: CSSStyleDeclaration => Unit,
                  frame2: CSSStyleDeclaration => Unit): Comp => Callback =
    $ => Callback {
      val n = $.getDOMNode()
      before(n.style)
      window.requestAnimationFrame{(_: Double) =>
        frame1(n.style)
        window.requestAnimationFrame((_: Double) =>
          frame2(n.style)
        )
      }
    }

  def outlineHighlighter(outlineCss: String) =
    highlighter(
      _.boxSizing = "border-box",
      s => {
        s.transition = "outline 0s"
        s.outline = outlineCss
      },
      s => {
        s.outline = "1px solid rgba(0,0,0,0)"
        s.transition = "outline 550ms linear"
      })

  val defaultHighlighter = outlineHighlighter("2px solid rgba(200,20,10,1)")

  case class Nodes(outer: Element, good: Element, bad: Element)

  implicit def apply(options: Options): Comp => ReusabilityOverlay =
    new DefaultReusabilityOverlay(_, options)
}

class DefaultReusabilityOverlay($: Comp, options: DefaultReusabilityOverlay.Options) extends ReusabilityOverlay with SetInterval {
  import DefaultReusabilityOverlay.{Nodes, autoLiftHtml}

  protected var good = 0
  protected var bad = Vector("Initial mount.")
  protected def badCount = bad.size
  protected var overlay: Option[Nodes] = None

  val onClick = Callback {
    if (bad.nonEmpty) {
      var i = options.reasonsToShowOnClick min badCount
      println(s"Last $i reasons to update:")
      for (r <- bad.takeRight(i)) {
        println(s"#$i: $r")
        i -= 1
      }
    }
    val sum = good + badCount
    if (sum != 0)
      printf("%d/%d (%.0f%%) updates prevented.\n", good, sum, good.toDouble / sum * 100)
  }

  val create = Callback {

    // Create
    val tmp = document.createElement("div")
    document.body.appendChild(tmp)
    React.render(options.template.template, tmp)
    val outer = tmp.firstChild
    document.body.replaceChild(outer, tmp)

    // Customise
    outer.addEventListener("click", onClick.toJsFunction1)

    // Store
    val good = options.template good outer
    val bad = options.template bad outer
    overlay = Some(Nodes(outer, good, bad))
  }

  def withNodes(f: Nodes => Unit): Callback =
    Callback(overlay foreach f)

  val updatePosition = withNodes { n =>
    val d = $.getDOMNode()
    val ds = d.getBoundingClientRect()
    val ns = n.outer.getBoundingClientRect()

    var y = window.pageYOffset + ds.top
    var x = ds.left

    if (d.tagName == "TABLE") {
      y -= ns.height
      x -= ns.width
    } else if (d.tagName == "TR")
      x -= ns.width

    n.outer.style.top  = y.toString + "px"
    n.outer.style.left = x.toString + "px"
  }

  val updateContent = withNodes { n =>
    val b = badCount
    n.good.innerHTML = good.toString
    n.bad.innerHTML = b.toString
    options.dynamicStyle(good, b, n)
    n.outer.setAttribute("title", "Last update reason:\n" + bad.lastOption.getOrElse("None"))
  }

  val update =
    updateContent >> updatePosition

  val highlightUpdate =
    options.updateHighlighter($)

  override val logGood =
    Callback(good += 1) >> update

  override def logBad(reason: String) =
    Callback(bad :+= reason) >> update >> highlightUpdate

  override val onMount =
    create >> update >> options.mountHighlighter($) >> setInterval(updatePosition, options.updatePositionEvery)

  override val onUnmount =
    super.unmount.thenRun(
      overlay.foreach { o =>
        document.body.removeChild(o.outer)
        overlay = None
      }
    )
}
