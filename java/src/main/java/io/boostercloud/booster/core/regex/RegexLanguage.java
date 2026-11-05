package io.boostercloud.booster.core.regex;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

/** Java-only bridge to TRegex's internal ECMAScript compiler. No JavaScript program is evaluated. */
@TruffleLanguage.Registration(id = RegexLanguage.ID, name = "Booster ECMAScript regular expressions", version = "1",
    dependentLanguages = {"regex"})
public final class RegexLanguage extends TruffleLanguage<TruffleLanguage.Env> {
  public static final String ID = "booster-regex";
  private static final ContextReference<Env> CONTEXT = ContextReference.create(RegexLanguage.class);
  @Override protected Env createContext(Env environment) { return environment; }
  @Override protected CallTarget parse(ParsingRequest request) {
    String expression = request.getSource().getCharacters().toString();
    return new RootNode(this) {
      @Override public Object execute(VirtualFrame frame) {
        return CONTEXT.get(this).parseInternal(Source.newBuilder("regex", expression, "read-model-filter")
            .mimeType("application/tregex").internal(true).cached(false).build()).call();
      }
    }.getCallTarget();
  }
}
