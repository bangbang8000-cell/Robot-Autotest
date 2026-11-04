import { defineComponent as je, openBlock as i, createElementBlock as u, normalizeClass as ne, createCommentVNode as T, renderSlot as Yn, inject as rs, computed as O, ref as N, onBeforeUnmount as $n, createVNode as ve, unref as p, createElementVNode as t, withModifiers as re, toDisplayString as m, Fragment as x, renderList as X, normalizeStyle as Ee, createBlock as Ut, reactive as gn, onMounted as qo, watch as Le, createTextVNode as we, withDirectives as q, vModelText as le, nextTick as Xe, onUnmounted as Ho, Teleport as yn, Transition as is, withCtx as _e, vModelSelect as it, withKeys as Ye, provide as us, mergeProps as Uo, normalizeProps as cs, guardReactiveProps as ds, shallowRef as ps, vShow as eo } from "vue";
import { useI18n as mt } from "vue-i18n";
import { Handle as St, Position as Lt, useVueFlow as fs, VueFlow as ms } from "@vue-flow/core";
import { Background as gs } from "@vue-flow/background";
import { Controls as vs } from "@vue-flow/controls";
import { MiniMap as hs } from "@vue-flow/minimap";
import Va from "dompurify";
import { defineStore as $o } from "pinia";
import { HighlightStyle as ys, StreamLanguage as bs, LanguageSupport as ws, syntaxHighlighting as sa, defaultHighlightStyle as _s } from "@codemirror/language";
import { tags as Re } from "@lezer/highlight";
import { EditorView as pn, lineNumbers as ks, highlightActiveLine as Cs, highlightSpecialChars as $s, keymap as Ts } from "@codemirror/view";
import { EditorState as la } from "@codemirror/state";
import { history as Es, defaultKeymap as Ss, historyKeymap as Ls } from "@codemirror/commands";
const Vs = ["disabled"], As = {
  key: 0,
  class: "spinner"
}, Ns = /* @__PURE__ */ je({
  __name: "BaseButton",
  props: {
    variant: {},
    size: {},
    loading: { type: Boolean },
    disabled: { type: Boolean }
  },
  emits: ["click"],
  setup(e) {
    return (n, r) => (i(), u("button", {
      class: ne(["btn", [`btn-${e.variant || "primary"}`, `btn-${e.size || "md"}`]]),
      disabled: e.disabled || e.loading,
      onClick: r[0] || (r[0] = (c) => n.$emit("click", c))
    }, [
      e.loading ? (i(), u("span", As)) : T("", !0),
      Yn(n.$slots, "default", {}, void 0, !0)
    ], 10, Vs));
  }
}), qe = (e, n) => {
  const r = e.__vccOpts || e;
  for (const [c, g] of n)
    r[c] = g;
  return r;
}, Oe = /* @__PURE__ */ qe(Ns, [["__scopeId", "data-v-de5bd678"]]), ra = 1;
function Rs(e) {
  const n = e.schema_version;
  if (n == null)
    throw new Error("RecordedFlow missing schema_version");
  if (typeof n != "number" || !Number.isInteger(n))
    throw new Error(`RecordedFlow schema_version must be integer, got ${typeof n}`);
  if (n > ra)
    throw new Error(
      `RecordedFlow schema_version ${n} newer than supported ${ra}. 请升级 Robot Autotest。`
    );
  if (n < 1)
    throw new Error(`RecordedFlow schema_version must be >= 1, got ${n}`);
}
function Fs(e) {
  return e.selector_candidates.length === 0 ? null : e.selector_candidates[e.active_candidate_index] ?? null;
}
function vn(e) {
  return e >= 80 ? "good" : e >= 50 ? "ok" : "poor";
}
const Ps = /^(Given|When|Then|And|But)\s+(.+)$/i;
function Aa(e) {
  const n = Ps.exec((e ?? "").trim());
  return n ? { prefix: n[1].charAt(0).toUpperCase() + n[1].slice(1).toLowerCase(), rest: n[2].trim() } : null;
}
function Ds(e) {
  let n = 0, r = !1, c = !1, g = -1;
  for (let a = 0; a < e.length; a++) {
    const _ = e[a];
    if (r) {
      _ === "'" && e[a - 1] !== "\\" && (r = !1);
      continue;
    }
    if (c) {
      _ === '"' && e[a - 1] !== "\\" && (c = !1);
      continue;
    }
    if (_ === "'") {
      r = !0;
      continue;
    }
    if (_ === '"') {
      c = !0;
      continue;
    }
    if (_ === "(" || _ === "[" || _ === "{") {
      n++;
      continue;
    }
    if (_ === ")" || _ === "]" || _ === "}") {
      n--;
      continue;
    }
    if (n === 0 && _ === "=") {
      if (e[a - 1] === " " && e[a + 1] === " ") return a;
      g === -1 && (g = a);
    }
  }
  return g;
}
function ta(e) {
  const n = e.trim(), r = (E) => ({
    name: "",
    type: null,
    defaultValue: null,
    kind: E
  });
  if (n === "/") return r("positional-only-sep");
  if (n === "*") return r("named-only-sep");
  if (n === "?") return r("optional-sep");
  let c = "positional", g = n;
  g.startsWith("**") ? (c = "kwargs", g = g.slice(2)) : g.startsWith("*") && (c = "varargs", g = g.slice(1));
  let a = null, _ = g;
  const y = Ds(g);
  if (y >= 0) {
    const E = g.slice(y + 1).trim();
    a = E === "" ? null : E, _ = g.slice(0, y).trimEnd();
  }
  a !== null && c === "positional" && (c = "optional");
  let k = _, A = null;
  const V = _.indexOf(":");
  return V >= 0 && (k = _.slice(0, V).trim(), A = _.slice(V + 1).trim(), A === "" && (A = null)), { name: k, type: A, defaultValue: a, kind: c };
}
function Ms(e, n) {
  const r = [];
  let c = "", g = 0, a = !1, _ = !1;
  for (let y = 0; y < e.length; y++) {
    const k = e[y];
    if ((a || _) && k === "\\" && y + 1 < e.length) {
      c += k + e[y + 1], y++;
      continue;
    }
    if (a) {
      k === "'" && (a = !1), c += k;
      continue;
    }
    if (_) {
      k === '"' && (_ = !1), c += k;
      continue;
    }
    if (k === "'") {
      a = !0, c += k;
      continue;
    }
    if (k === '"') {
      _ = !0, c += k;
      continue;
    }
    if (k === "[" || k === "(" || k === "{") {
      g++, c += k;
      continue;
    }
    if (k === "]" || k === ")" || k === "}") {
      g--, c += k;
      continue;
    }
    if (g === 0 && k === n) {
      r.push(c), c = "";
      continue;
    }
    c += k;
  }
  return r.push(c), r;
}
function xs(e) {
  const n = /^(?:Literal|OneOf)\s*\[(.+)\]\s*$/is.exec(e);
  if (!n) return null;
  const r = n[1].trim();
  if (!r) return [];
  const c = [];
  let g = "", a = !1, _ = !1, y = 0;
  for (let k = 0; k < r.length; k++) {
    const A = r[k];
    if ((a || _) && A === "\\" && k + 1 < r.length) {
      g += A + r[k + 1], k++;
      continue;
    }
    if (a) {
      A === "'" ? a = !1 : g += A;
      continue;
    }
    if (_) {
      A === '"' ? _ = !1 : g += A;
      continue;
    }
    if (A === "'") {
      a = !0;
      continue;
    }
    if (A === '"') {
      _ = !0;
      continue;
    }
    if (A === "[" || A === "(" || A === "{") {
      y++, g += A;
      continue;
    }
    if (A === "]" || A === ")" || A === "}") {
      y--, g += A;
      continue;
    }
    if (A === "," && y === 0) {
      g.trim() && c.push(g.trim()), g = "";
      continue;
    }
    g += A;
  }
  return g.trim() && c.push(g.trim()), c;
}
function bn(e) {
  const n = (e ?? "").trim(), r = /^Optional\s*\[\s*(.+)\s*\]\s*$/is.exec(n);
  if (r)
    return { ...bn(r[1]), optional: !0, raw: n };
  if (n.includes("|")) {
    const A = Ms(n, "|").map((E) => E.trim()).filter(Boolean);
    if (A.some((E) => E === "None") && A.length > 1) {
      const E = A.filter((I) => I !== "None").join(" | ");
      return { ...bn(E), optional: !0, raw: n };
    }
  }
  const c = n.replace(/^\*\*?/, "").trim(), g = xs(c);
  let a = "?", _ = "flowEditor.argTypes.unknown", y = "text", k = null;
  return n === "" || (g ? (a = "▼", _ = "flowEditor.argTypes.choice", y = "select", k = g) : /^str$/i.test(c) ? (a = "Aa", _ = "flowEditor.argTypes.text", y = "text") : /^int$/i.test(c) ? (a = "123", _ = "flowEditor.argTypes.integer", y = "integer") : /^float$|^number$/i.test(c) ? (a = "1.0", _ = "flowEditor.argTypes.number", y = "number") : /^bool(ean)?$/i.test(c) ? (a = "✓", _ = "flowEditor.argTypes.yesNo", y = "checkbox") : /^timedelta$/i.test(c) ? (a = "⏱", _ = "flowEditor.argTypes.duration", y = "duration") : /^(pathlib\.)?path$/i.test(c) ? (a = "📁", _ = "flowEditor.argTypes.path", y = "text") : /^any$/i.test(c) ? (a = "*", _ = "flowEditor.argTypes.any", y = "text") : /^(dict|list|tuple|set|sequence|mapping|iterable)\b/i.test(c) && (a = "[ ]", _ = "flowEditor.argTypes.collection", y = "text")), { icon: a, labelKey: _, control: y, optional: !1, choices: k, raw: n };
}
const Us = /* @__PURE__ */ new Set(["true", "yes", "on", "1"]);
function ia(e) {
  return e ? Us.has(e.trim().toLowerCase()) : !1;
}
function ua(e) {
  return e ? "True" : "False";
}
function Bs(e) {
  return e ? /^[$@&]\{.+\}$/.test(e.trim()) : !1;
}
function Na(e, n, r) {
  const c = () => r("flowEditor.argLabels.fallback", { n: n + 1 });
  if (!e || e.length === 0) return c();
  const g = e.findIndex((y) => y.kind === "varargs"), a = e[n];
  if (a)
    return a.kind === "varargs" ? "1" : a.kind === "kwargs" ? r("flowEditor.argLabels.extraNamed") : a.name ? a.name : c();
  const _ = e[e.length - 1];
  return g >= 0 && (_ == null ? void 0 : _.kind) === "varargs" ? String(n - g + 1) : (_ == null ? void 0 : _.kind) === "kwargs" ? r("flowEditor.argLabels.extraNamed") : c();
}
const _o = /* @__PURE__ */ new Map([
  // =====================================================================
  // BuiltIn Library
  // =====================================================================
  ["call method", ["object", "method_name", "*args"]],
  ["catenate", ["*items"]],
  ["comment", ["*messages"]],
  ["continue for loop", []],
  ["continue for loop if", ["condition"]],
  ["convert to binary", ["item", "base=None", "prefix=None", "length=None"]],
  ["convert to boolean", ["item"]],
  ["convert to bytes", ["input", "input_type=text"]],
  ["convert to hex", ["item", "base=None", "prefix=None", "length=None", "lowercase=False"]],
  ["convert to integer", ["item", "base=None"]],
  ["convert to number", ["item", "precision=None"]],
  ["convert to octal", ["item", "base=None", "prefix=None", "length=None"]],
  ["convert to string", ["item"]],
  ["create dictionary", ["*items"]],
  ["create list", ["*items"]],
  ["evaluate", ["expression", "modules=None", "namespace=None"]],
  ["exit for loop", []],
  ["exit for loop if", ["condition"]],
  ["fail", ["msg=None", "*tags"]],
  ["fatal error", ["msg=None"]],
  ["get count", ["container", "item"]],
  ["get length", ["item"]],
  ["get library instance", ["name", "all=False"]],
  ["get time", ["format=timestamp", "time_=NOW"]],
  ["get variable value", ["name", "default=None"]],
  ["get variables", ["no_decoration=False"]],
  ["import library", ["name", "*args"]],
  ["import resource", ["path"]],
  ["import variables", ["path", "*args"]],
  ["keyword should exist", ["name", "msg=None"]],
  ["length should be", ["item", "length", "msg=None"]],
  ["log", ["message", "level=INFO", "html=False", "console=False", "repr=False"]],
  ["log many", ["*messages"]],
  ["log to console", ["message", "stream=STDOUT", "no_newline=False"]],
  ["log variables", ["level=INFO"]],
  ["no operation", []],
  ["pass execution", ["message", "*tags"]],
  ["pass execution if", ["condition", "message", "*tags"]],
  ["regexp escape", ["*patterns"]],
  ["reload library", ["name_or_instance"]],
  ["remove tags", ["*tags"]],
  ["repeat keyword", ["repeat", "name", "*args"]],
  ["replace variables", ["text"]],
  ["return from keyword", ["*return_values"]],
  ["return from keyword if", ["condition", "*return_values"]],
  ["run keyword", ["name", "*args"]],
  ["run keyword and continue on failure", ["name", "*args"]],
  ["run keyword and expect error", ["expected_error", "name", "*args"]],
  ["run keyword and ignore error", ["name", "*args"]],
  ["run keyword and return", ["name", "*args"]],
  ["run keyword and return if", ["condition", "name", "*args"]],
  ["run keyword and return status", ["name", "*args"]],
  ["run keyword and warn on failure", ["name", "*args"]],
  ["run keyword if", ["condition", "name", "*args"]],
  ["run keyword if all tests passed", ["name", "*args"]],
  ["run keyword if any tests failed", ["name", "*args"]],
  ["run keyword if test failed", ["name", "*args"]],
  ["run keyword if test passed", ["name", "*args"]],
  ["run keyword if timeout occurred", ["name", "*args"]],
  ["run keyword unless", ["condition", "name", "*args"]],
  ["run keywords", ["*keywords"]],
  ["set global variable", ["name", "*values"]],
  ["set library search order", ["*search_order"]],
  ["set local variable", ["name", "*values"]],
  ["set log level", ["level"]],
  ["set suite documentation", ["doc", "append=False", "top=False"]],
  ["set suite metadata", ["name", "value", "append=False", "top=False"]],
  ["set suite variable", ["name", "*values"]],
  ["set tags", ["*tags"]],
  ["set task variable", ["name", "*values"]],
  ["set test documentation", ["doc", "append=False"]],
  ["set test message", ["message", "append=False"]],
  ["set test variable", ["name", "*values"]],
  ["set variable", ["*values"]],
  ["set variable if", ["condition", "*values"]],
  ["should be empty", ["item", "msg=None"]],
  ["should be equal", ["first", "second", "msg=None", "values=True", "ignore_case=False"]],
  ["should be equal as integers", ["first", "second", "msg=None", "values=True", "base=None"]],
  ["should be equal as numbers", ["first", "second", "msg=None", "values=True", "precision=6"]],
  ["should be equal as strings", ["first", "second", "msg=None", "values=True", "ignore_case=False"]],
  ["should be true", ["condition", "msg=None"]],
  ["should contain", ["container", "item", "msg=None", "values=True", "ignore_case=False"]],
  ["should contain any", ["container", "*items", "msg=None"]],
  ["should contain x times", ["container", "item", "count", "msg=None", "ignore_case=False"]],
  ["should end with", ["str1", "str2", "msg=None", "values=True", "ignore_case=False"]],
  ["should match", ["string", "pattern", "msg=None", "values=True", "ignore_case=False"]],
  ["should match regexp", ["string", "pattern", "msg=None", "values=True"]],
  ["should not be empty", ["item", "msg=None"]],
  ["should not be equal", ["first", "second", "msg=None", "values=True", "ignore_case=False"]],
  ["should not be equal as integers", ["first", "second", "msg=None", "values=True", "base=None"]],
  ["should not be equal as numbers", ["first", "second", "msg=None", "values=True", "precision=6"]],
  ["should not be equal as strings", ["first", "second", "msg=None", "values=True", "ignore_case=False"]],
  ["should not be true", ["condition", "msg=None"]],
  ["should not contain", ["container", "item", "msg=None", "values=True", "ignore_case=False"]],
  ["should not contain any", ["container", "*items", "msg=None"]],
  ["should not end with", ["str1", "str2", "msg=None", "values=True", "ignore_case=False"]],
  ["should not match", ["string", "pattern", "msg=None", "values=True", "ignore_case=False"]],
  ["should not match regexp", ["string", "pattern", "msg=None", "values=True"]],
  ["should not start with", ["str1", "str2", "msg=None", "values=True", "ignore_case=False"]],
  ["should start with", ["str1", "str2", "msg=None", "values=True", "ignore_case=False"]],
  ["skip", ["msg=None"]],
  ["skip if", ["condition", "msg=None"]],
  ["sleep", ["time_", "reason=None"]],
  ["variable should exist", ["name", "msg=None"]],
  ["variable should not exist", ["name", "msg=None"]],
  ["wait until keyword succeeds", ["retry", "retry_interval", "name", "*args"]],
  // =====================================================================
  // String Library
  // =====================================================================
  ["convert to lower case", ["string"]],
  ["convert to upper case", ["string"]],
  ["decode bytes to string", ["bytes", "encoding=UTF-8"]],
  ["encode string to bytes", ["string", "encoding=UTF-8"]],
  ["fetch from left", ["string", "marker"]],
  ["fetch from right", ["string", "marker"]],
  ["format string", ["template", "*args", "**kwargs"]],
  ["generate random string", ["length=8", "chars=[LETTERS][NUMBERS]"]],
  ["get line", ["string", "line_number"]],
  ["get line count", ["string"]],
  ["get lines containing string", ["string", "pattern", "case_insensitive=False"]],
  ["get lines matching pattern", ["string", "pattern", "case_insensitive=False"]],
  ["get lines matching regexp", ["string", "pattern"]],
  ["get regexp matches", ["string", "pattern", "*groups"]],
  ["get substring", ["string", "start", "end=None"]],
  ["remove string", ["string", "*removables"]],
  ["remove string using regexp", ["string", "*patterns"]],
  ["replace string", ["string", "search_for", "replace_with", "count=-1"]],
  ["replace string using regexp", ["string", "pattern", "replace_with", "count=0"]],
  ["should be byte string", ["item", "msg=None"]],
  ["should be lower case", ["string", "msg=None"]],
  ["should be string", ["item", "msg=None"]],
  ["should be title case", ["string", "msg=None", "exclude=None"]],
  ["should be unicode string", ["item", "msg=None"]],
  ["should be upper case", ["string", "msg=None"]],
  ["should not be string", ["item", "msg=None"]],
  ["split string", ["string", "separator=None", "max_split=-1"]],
  ["split string from right", ["string", "separator=None", "max_split=-1"]],
  ["split string to characters", ["string"]],
  ["split to lines", ["string", "start=0", "end=None"]],
  ["strip string", ["string", "mode=both", "characters=None"]],
  // =====================================================================
  // Collections Library
  // =====================================================================
  ["append to list", ["list_", "*values"]],
  ["combine lists", ["*lists"]],
  ["convert to list", ["item"]],
  ["copy dictionary", ["dictionary", "deepcopy=False"]],
  ["copy list", ["list_", "deepcopy=False"]],
  ["count values in list", ["list_", "value", "start=0", "end=None"]],
  ["dictionaries should be equal", ["dict1", "dict2", "msg=None", "values=True"]],
  ["dictionary should contain item", ["dictionary", "key", "value", "msg=None"]],
  ["dictionary should contain key", ["dictionary", "key", "msg=None"]],
  ["dictionary should contain sub dictionary", ["dict1", "dict2", "msg=None", "values=True"]],
  ["dictionary should contain value", ["dictionary", "value", "msg=None"]],
  ["dictionary should not contain key", ["dictionary", "key", "msg=None"]],
  ["dictionary should not contain value", ["dictionary", "value", "msg=None"]],
  ["get dictionary items", ["dictionary", "sort_keys=True"]],
  ["get dictionary keys", ["dictionary", "sort_keys=True"]],
  ["get dictionary values", ["dictionary", "sort_keys=True"]],
  ["get from dictionary", ["dictionary", "key", "default=None"]],
  ["get from list", ["list_", "index"]],
  ["get match count", ["list", "pattern", "case_insensitive=False", "whitespace_insensitive=False"]],
  ["get matches", ["list", "pattern", "case_insensitive=False", "whitespace_insensitive=False"]],
  ["get slice from list", ["list_", "start=0", "end=None"]],
  ["insert into list", ["list_", "index", "value"]],
  ["keep in dictionary", ["dictionary", "*keys"]],
  ["list should contain sub list", ["list1", "list2", "msg=None", "values=True"]],
  ["list should contain value", ["list_", "value", "msg=None"]],
  ["list should not contain duplicates", ["list_", "msg=None"]],
  ["list should not contain value", ["list_", "value", "msg=None"]],
  ["lists should be equal", ["list1", "list2", "msg=None", "values=True", "names=None"]],
  ["log dictionary", ["dictionary", "level=INFO"]],
  ["log list", ["list_", "level=INFO"]],
  ["pop from dictionary", ["dictionary", "key", "default="]],
  ["remove duplicates", ["list_"]],
  ["remove from dictionary", ["dictionary", "*keys"]],
  ["remove from list", ["list_", "index"]],
  ["remove values from list", ["list_", "*values"]],
  ["reverse list", ["list_"]],
  ["set list value", ["list_", "index", "value"]],
  ["set to dictionary", ["dictionary", "*key_value_pairs"]],
  ["should contain match", ["list", "pattern", "msg=None", "case_insensitive=False", "whitespace_insensitive=False"]],
  ["should not contain match", ["list", "pattern", "msg=None", "case_insensitive=False", "whitespace_insensitive=False"]],
  ["sort list", ["list_"]],
  // =====================================================================
  // DateTime Library
  // =====================================================================
  ["add time to date", ["date", "time", "result_format=timestamp", "exclude_millis=False", "date_format=None"]],
  ["add time to time", ["time1", "time2", "result_format=number", "exclude_millis=False"]],
  ["convert date", ["date", "result_format=timestamp", "exclude_millis=False", "date_format=None"]],
  ["convert time", ["time", "result_format=number", "exclude_millis=False"]],
  ["get current date", ["time_zone=local", "increment=0", "result_format=timestamp", "exclude_millis=False"]],
  ["subtract date from date", ["date1", "date2", "result_format=number", "exclude_millis=False", "date1_format=None", "date2_format=None"]],
  ["subtract time from date", ["date", "time", "result_format=timestamp", "exclude_millis=False", "date_format=None"]],
  ["subtract time from time", ["time1", "time2", "result_format=number", "exclude_millis=False"]],
  // =====================================================================
  // OperatingSystem Library
  // =====================================================================
  ["append to environment variable", ["name", "*values", "separator="]],
  ["append to file", ["path", "content", "encoding=UTF-8"]],
  ["copy directory", ["source", "destination"]],
  ["copy file", ["source", "destination"]],
  ["copy files", ["*sources_and_destination"]],
  ["count directories in directory", ["path", "pattern=None"]],
  ["count files in directory", ["path", "pattern=None"]],
  ["count items in directory", ["path", "pattern=None"]],
  ["create binary file", ["path", "content"]],
  ["create directory", ["path"]],
  ["create file", ["path", "content=", "encoding=UTF-8"]],
  ["directory should be empty", ["path", "msg=None"]],
  ["directory should exist", ["path", "msg=None"]],
  ["directory should not be empty", ["path", "msg=None"]],
  ["directory should not exist", ["path", "msg=None"]],
  ["empty directory", ["path"]],
  ["environment variable should be set", ["name", "msg=None"]],
  ["environment variable should not be set", ["name", "msg=None"]],
  ["file should be empty", ["path", "msg=None"]],
  ["file should exist", ["path", "msg=None"]],
  ["file should not be empty", ["path", "msg=None"]],
  ["file should not exist", ["path", "msg=None"]],
  ["get binary file", ["path"]],
  ["get environment variable", ["name", "default=None"]],
  ["get environment variables", []],
  ["get file", ["path", "encoding=UTF-8"]],
  ["get file size", ["path"]],
  ["get modified time", ["path", "format=timestamp"]],
  ["grep file", ["path", "pattern", "encoding=UTF-8"]],
  ["join path", ["base", "*parts"]],
  ["join paths", ["base", "*paths"]],
  ["list directories in directory", ["path", "pattern=None", "absolute=False"]],
  ["list directory", ["path", "pattern=None", "absolute=False"]],
  ["list files in directory", ["path", "pattern=None", "absolute=False"]],
  ["log environment variables", ["level=INFO"]],
  ["log file", ["path", "encoding=UTF-8"]],
  ["move directory", ["source", "destination"]],
  ["move file", ["source", "destination"]],
  ["move files", ["*sources_and_destination"]],
  ["normalize path", ["path"]],
  ["remove directory", ["path", "recursive=False"]],
  ["remove environment variable", ["*names"]],
  ["remove file", ["path"]],
  ["remove files", ["*paths"]],
  ["run", ["command"]],
  ["run and return rc", ["command"]],
  ["run and return rc and output", ["command"]],
  ["set environment variable", ["name", "value"]],
  ["set modified time", ["path", "mtime=None"]],
  ["should exist", ["path", "msg=None"]],
  ["should not exist", ["path", "msg=None"]],
  ["split extension", ["path"]],
  ["split path", ["path"]],
  ["touch", ["path"]],
  ["wait until created", ["path", "timeout=1 minute"]],
  ["wait until removed", ["path", "timeout=1 minute"]],
  // =====================================================================
  // Process Library
  // =====================================================================
  ["get process id", ["handle=None"]],
  ["get process object", ["handle=None"]],
  ["get process result", ["handle=None", "rc=False", "stdout=False", "stderr=False", "stdout_path=False", "stderr_path=False"]],
  ["is process running", ["handle=None"]],
  ["join command line", ["*args"]],
  ["process should be running", ["handle=None", "error_message=Process is not running."]],
  ["process should be stopped", ["handle=None", "error_message=Process is running."]],
  ["run process", ["command", "*arguments", "**configuration"]],
  ["send signal to process", ["signal", "handle=None", "group=False"]],
  ["split command line", ["args", "escaping=False"]],
  ["start process", ["command", "*arguments", "**configuration"]],
  ["switch process", ["handle"]],
  ["terminate all processes", ["kill=False"]],
  ["terminate process", ["handle=None", "kill=False"]],
  ["wait for process", ["handle=None", "timeout=None", "on_timeout=continue"]],
  // =====================================================================
  // XML Library
  // =====================================================================
  ["add element", ["source", "element", "index=None", "xpath=."]],
  ["clear element", ["source", "xpath=.", "clear_tail=False"]],
  ["copy element", ["source", "xpath=."]],
  ["element attribute should be", ["source", "name", "expected", "xpath=.", "message=None"]],
  ["element attribute should match", ["source", "name", "pattern", "xpath=.", "message=None"]],
  ["element should exist", ["source", "xpath=.", "message=None"]],
  ["element should not exist", ["source", "xpath=.", "message=None"]],
  ["element should not have attribute", ["source", "name", "xpath=.", "message=None"]],
  ["element text should be", ["source", "expected", "xpath=.", "normalize_whitespace=False", "message=None"]],
  ["element text should match", ["source", "pattern", "xpath=.", "normalize_whitespace=False", "message=None"]],
  ["element to string", ["source", "xpath=.", "encoding=None"]],
  ["elements should be equal", ["source", "expected", "exclude_children=False", "normalize_whitespace=False"]],
  ["elements should match", ["source", "expected", "exclude_children=False", "normalize_whitespace=False"]],
  ["evaluate xpath", ["source", "expression", "context=."]],
  ["get child elements", ["source", "xpath=."]],
  ["get element", ["source", "xpath=."]],
  ["get element attribute", ["source", "name", "xpath=.", "default=None"]],
  ["get element attributes", ["source", "xpath=."]],
  ["get element count", ["source", "xpath=."]],
  ["get element text", ["source", "xpath=.", "normalize_whitespace=False"]],
  ["get elements", ["source", "xpath=."]],
  ["get elements texts", ["source", "xpath=.", "normalize_whitespace=False"]],
  ["log element", ["source", "level=INFO", "xpath=."]],
  ["parse xml", ["source", "keep_clark_notation=False", "strip_namespaces=False"]],
  ["remove element", ["source", "xpath=", "remove_tail=False"]],
  ["remove element attribute", ["source", "name", "xpath=."]],
  ["remove element attributes", ["source", "xpath=."]],
  ["remove elements", ["source", "xpath=", "remove_tail=False"]],
  ["remove elements attribute", ["source", "name", "xpath=."]],
  ["remove elements attributes", ["source", "xpath=."]],
  ["save xml", ["source", "path", "encoding=UTF-8"]],
  ["set element attribute", ["source", "name", "value", "xpath=."]],
  ["set element tag", ["source", "tag", "xpath=."]],
  ["set element text", ["source", "text=None", "tail=None", "xpath=."]],
  ["set elements attribute", ["source", "name", "value", "xpath=."]],
  ["set elements tag", ["source", "tag", "xpath=."]],
  ["set elements text", ["source", "text=None", "tail=None", "xpath=."]]
]), Ra = 200, ca = /%\{([^}=]+)(?:=([^}]*))?\}/g;
function Ks(e) {
  const n = [];
  if (!e) return n;
  ca.lastIndex = 0;
  let r;
  for (; (r = ca.exec(e)) !== null; )
    n.push({ name: r[1].trim(), default: r[2] !== void 0 ? r[2] : null });
  return n;
}
function Fa(e) {
  const n = /* @__PURE__ */ new Map();
  for (const r of e)
    for (const c of Ks(r)) {
      const g = n.get(c.name);
      g ? g.default === null && c.default !== null && n.set(c.name, c) : n.set(c.name, c);
    }
  return [...n.values()];
}
function Pa(e, n) {
  return e.map((r) => ({
    ref: r,
    state: n === null ? "unknown" : n.has(r.name) ? "defined" : r.default !== null ? "default" : "missing"
  }));
}
const Hs = { class: "flow-node-header" }, Os = ["draggable"], Is = {
  key: 1,
  class: "flow-node-bdd-badge",
  "data-testid": "bdd-badge"
}, Ws = { class: "flow-node-label" }, js = ["title"], qs = {
  key: 0,
  class: "flow-node-args"
}, zs = ["title"], Gs = ["title"], Ys = {
  key: 1,
  class: "flow-arg-type-icon-prefix",
  "data-testid": "arg-type-icon"
}, Xs = {
  key: 2,
  class: "flow-arg-name-prefix",
  "data-testid": "arg-name-prefix"
}, Zs = { class: "flow-arg-value" }, Qs = ["title"], Js = {
  key: 1,
  class: "flow-node-return"
}, el = /* @__PURE__ */ je({
  __name: "KeywordNode",
  props: {
    data: {},
    reorderEnabled: { type: Boolean },
    selected: { type: Boolean }
  },
  emits: ["reorder-drag-start"],
  setup(e, { emit: n }) {
    const r = e, c = n, { t: g } = mt(), a = rs("envVarKeys", null), _ = O(() => new Set(
      Pa(r.data.envRefs ?? [], (a == null ? void 0 : a.value) ?? null).filter((R) => R.state === "missing").map((R) => R.ref.name)
    )), y = O(() => (r.data.envRefs ?? []).map(
      (R) => (R.default !== null ? `%{${R.name}=${R.default}}` : `%{${R.name}}`) + (_.value.has(R.name) ? ` ${g("flowEditor.envVarNotDefined")}` : "")
    ).join(`
`)), k = N(!1);
    let A = null;
    function V() {
      E(), A = window.setTimeout(() => {
        k.value = !0;
      }, Ra);
    }
    function E() {
      A != null && (clearTimeout(A), A = null), k.value = !1;
    }
    $n(E);
    function B(R) {
      c("reorder-drag-start", R);
    }
    function I() {
      E();
    }
    function j(R) {
      const H = r.data.argSpecs;
      if (!H || H.length === 0) return null;
      const Y = Na(H, R, g), U = g("flowEditor.argLabels.fallback", { n: R + 1 });
      return Y === U ? null : Y;
    }
    function ue(R) {
      var Z;
      const H = r.data.step.args[R], Y = (Z = r.data.argSpecs) == null ? void 0 : Z[R], U = [];
      if (H && H.length > 0 && U.push(H), Y != null && Y.name) {
        let ce = Y.name;
        Y.type && (ce += `: ${Y.type}`), Y.defaultValue != null && (ce += ` = ${Y.defaultValue}`), U.push(ce);
      }
      return U.length > 0 ? U.join(`
`) : void 0;
    }
    function M(R) {
      var U;
      const H = (U = r.data.argSpecs) == null ? void 0 : U[R];
      if (!(H != null && H.type)) return null;
      const Y = bn(H.type);
      return Y.labelKey === "flowEditor.argTypes.unknown" ? null : Y.icon;
    }
    const J = O(() => {
      var R;
      return ((R = r.data.recording) == null ? void 0 : R.selector_candidates.length) ?? 0;
    }), ae = O(() => J.value > 0), z = O(() => {
      const R = r.data.recording ? Fs(r.data.recording) : null;
      return vn((R == null ? void 0 : R.quality_score) ?? 0);
    }), D = O(
      () => g("flowEditor.selector.tooltipHasCandidates", { count: J.value })
    );
    return (R, H) => (i(), u("div", {
      class: ne(["flow-node flow-node-keyword", { "flow-node--selected": e.selected }])
    }, [
      ve(p(St), {
        type: "target",
        position: p(Lt).Top
      }, null, 8, ["position"]),
      t("div", Hs, [
        e.reorderEnabled ? (i(), u("div", {
          key: 0,
          class: ne(["flow-drag-handle", { "flow-drag-handle--armed": k.value }]),
          draggable: k.value,
          onPointerdown: re(V, ["stop"]),
          onPointerup: E,
          onPointerleave: E,
          onPointercancel: E,
          onMousedown: H[0] || (H[0] = re(() => {
          }, ["stop"])),
          onDragstart: re(B, ["stop"]),
          onDragend: I
        }, "☰", 42, Os)) : T("", !0),
        H[1] || (H[1] = t("span", { class: "flow-node-icon" }, "⚙", -1)),
        e.data.bdd ? (i(), u("span", Is, m(e.data.bdd.prefix), 1)) : T("", !0),
        t("span", Ws, m(e.data.bdd ? e.data.bdd.rest : e.data.step.keyword || "Keyword"), 1),
        e.data.envRefs && e.data.envRefs.length ? (i(), u("span", {
          key: 2,
          class: ne(["flow-node-env-badge", { "flow-node-env-badge--missing": _.value.size > 0 }]),
          "data-testid": "env-badge",
          title: y.value
        }, "%{}", 10, js)) : T("", !0)
      ]),
      e.data.step.args.length ? (i(), u("div", qs, [
        (i(!0), u(x, null, X(e.data.step.args, (Y, U) => (i(), u("span", {
          key: U,
          class: ne(["flow-arg", { "flow-arg--has-candidates": U === 0 && ae.value }]),
          title: ue(U)
        }, [
          U === 0 && ae.value ? (i(), u("span", {
            key: 0,
            class: ne(["flow-arg-dot", `flow-arg-dot--${z.value}`]),
            title: D.value,
            "aria-hidden": "true"
          }, null, 10, Gs)) : T("", !0),
          M(U) ? (i(), u("span", Ys, m(M(U)), 1)) : T("", !0),
          j(U) ? (i(), u("span", Xs, m(j(U)) + ": ", 1)) : T("", !0),
          t("span", Zs, m(Y), 1),
          U === 0 && ae.value ? (i(), u("span", {
            key: 3,
            class: "flow-arg-count",
            title: D.value,
            "data-testid": "selector-candidate-count"
          }, m(p(g)("flowEditor.selector.candidatesBadge", { count: J.value })), 9, Qs)) : T("", !0)
        ], 10, zs))), 128))
      ])) : T("", !0),
      e.data.step.returnVars.length ? (i(), u("div", Js, m(e.data.step.returnVars.join(", ")) + " = ", 1)) : T("", !0),
      ve(p(St), {
        type: "source",
        position: p(Lt).Bottom
      }, null, 8, ["position"])
    ], 2));
  }
}), da = /* @__PURE__ */ qe(el, [["__scopeId", "data-v-dcd7105e"]]), tl = { class: "flow-node-header" }, ol = ["draggable"], nl = ["innerHTML"], al = { class: "flow-node-type" }, sl = {
  key: 0,
  class: "flow-node-condition"
}, ll = {
  key: 1,
  class: "flow-node-condition"
}, rl = {
  key: 2,
  class: "flow-node-condition"
}, il = /* @__PURE__ */ je({
  __name: "ControlNode",
  props: {
    data: {},
    reorderEnabled: { type: Boolean },
    selected: { type: Boolean }
  },
  emits: ["reorder-drag-start"],
  setup(e, { emit: n }) {
    const r = n, c = N(!1);
    let g = null;
    function a() {
      _(), g = window.setTimeout(() => {
        c.value = !0;
      }, Ra);
    }
    function _() {
      g != null && (clearTimeout(g), g = null), c.value = !1;
    }
    $n(_);
    function y(E) {
      r("reorder-drag-start", E);
    }
    function k() {
      _();
    }
    const A = {
      if: "#E8A838",
      else_if: "#E8A838",
      else: "#E8A838",
      for: "#7B61FF",
      while: "#7B61FF",
      try: "#38B2AC",
      except: "#E53E3E",
      finally: "#38B2AC"
    }, V = {
      if: "&#x2753;",
      // ?
      else_if: "&#x2753;",
      else: "&#x2194;",
      // ↔
      for: "&#x1F503;",
      // 🔃
      while: "&#x1F503;",
      try: "&#x1F6E1;",
      // 🛡
      except: "&#x26A0;",
      // ⚠
      finally: "&#x2705;"
      // ✅
    };
    return (E, B) => (i(), u("div", {
      class: ne(["flow-node flow-node-control", { "flow-node--selected": e.selected }]),
      style: Ee({ borderColor: A[e.data.stepType] || "#888" })
    }, [
      ve(p(St), {
        type: "target",
        position: p(Lt).Top
      }, null, 8, ["position"]),
      t("div", tl, [
        e.reorderEnabled ? (i(), u("div", {
          key: 0,
          class: ne(["flow-drag-handle", { "flow-drag-handle--armed": c.value }]),
          draggable: c.value,
          onPointerdown: re(a, ["stop"]),
          onPointerup: _,
          onPointerleave: _,
          onPointercancel: _,
          onMousedown: B[0] || (B[0] = re(() => {
          }, ["stop"])),
          onDragstart: re(y, ["stop"]),
          onDragend: k
        }, "☰", 42, ol)) : T("", !0),
        t("span", {
          class: "flow-node-icon",
          innerHTML: V[e.data.stepType] || "◆"
        }, null, 8, nl),
        t("span", al, m(e.data.stepType.toUpperCase().replace("_", " ")), 1)
      ]),
      e.data.step.condition ? (i(), u("div", sl, m(e.data.step.condition), 1)) : T("", !0),
      e.data.stepType === "for" || e.data.stepType === "while" ? (i(), u("div", ll, m(e.data.step.loopVar) + " " + m(e.data.step.loopFlavor) + " " + m(e.data.step.loopValues.join("  ")), 1)) : T("", !0),
      e.data.step.exceptPattern ? (i(), u("div", rl, m(e.data.step.exceptPattern), 1)) : T("", !0),
      ve(p(St), {
        type: "source",
        position: p(Lt).Bottom
      }, null, 8, ["position"])
    ], 6));
  }
}), ul = /* @__PURE__ */ qe(il, [["__scopeId", "data-v-b5f0e3b6"]]), cl = /* @__PURE__ */ je({
  __name: "ControlGroupNode",
  props: {
    data: {}
  },
  setup(e) {
    return (n, r) => (i(), u("div", {
      class: "control-frame",
      style: Ee({
        borderColor: e.data.borderColor,
        background: e.data.bgColor,
        width: "100%",
        height: "100%"
      })
    }, null, 4));
  }
}), dl = /* @__PURE__ */ qe(cl, [["__scopeId", "data-v-5c4a8d41"]]), pl = { class: "flow-terminal-label" }, fl = /* @__PURE__ */ je({
  __name: "StartEndNode",
  props: {
    data: {},
    type: {}
  },
  setup(e) {
    const n = e, { t: r } = mt(), c = O(() => {
      var g;
      return (g = n.data) != null && g.label && n.data.label.trim() ? n.data.label : n.type === "start" ? r("flowEditor.startNodeLabel") : r("flowEditor.endNodeLabel");
    });
    return (g, a) => (i(), u("div", {
      class: ne(["flow-node-terminal", e.type === "start" ? "flow-node-start" : "flow-node-end"])
    }, [
      e.type === "end" ? (i(), Ut(p(St), {
        key: 0,
        type: "target",
        position: p(Lt).Top
      }, null, 8, ["position"])) : T("", !0),
      e.type === "start" ? (i(), Ut(p(St), {
        key: 1,
        type: "target",
        position: p(Lt).Left,
        id: "left",
        style: { opacity: "0", "pointer-events": "none" }
      }, null, 8, ["position"])) : T("", !0),
      t("span", pl, m(c.value), 1),
      e.type === "start" ? (i(), Ut(p(St), {
        key: 2,
        type: "source",
        position: p(Lt).Bottom
      }, null, 8, ["position"])) : T("", !0)
    ], 2));
  }
}), pa = /* @__PURE__ */ qe(fl, [["__scopeId", "data-v-088ff712"]]), wn = gn({ scopeId: null, filePath: "" });
function S_(e) {
  Object.assign(wn, e);
}
function L_() {
  wn.scopeId = null, wn.filePath = "";
}
function Da() {
  return wn;
}
const fn = (e, n = "") => ({
  path: e,
  name: e.split("/").pop() ?? e,
  content: n,
  extension: e.includes(".") ? e.slice(e.lastIndexOf(".") + 1) : null,
  line_count: n ? n.split(/\r\n|\r|\n/).length : 0
}), We = (e) => () => {
  throw new Error(`编辑器适配器未实现 ${e}（请宿主通过 configureEditorAdapter 注入）`);
}, Ma = {
  searchKeywords: async () => ({ results: [] }),
  invalidateKeywordCache: async () => {
  },
  getEnvironmentKeywords: async () => ({ keywords: [], status: "unavailable" }),
  getTree: async () => ({ name: "", path: "", type: "directory", extension: null, children: [], test_count: 0 }),
  getFile: async (e, n) => fn(n),
  saveFile: async (e, n, r) => fn(n, r),
  createFile: async (e, n, r = "") => fn(n, r),
  deleteFile: async () => {
  },
  renameFile: async (e, n, r) => fn(r),
  search: async () => [],
  getTestCases: async () => [],
  getEnvironments: async () => [],
  getPackages: async () => [],
  getInstalledPackages: async () => [],
  installPackage: We("installPackage"),
  uninstallPackage: async () => {
  },
  getVariables: async () => [],
  createVariable: We("createVariable"),
  updateVariable: We("updateVariable"),
  deleteVariable: async () => {
  },
  createEnvironment: We("createEnvironment"),
  deleteEnvironment: async () => {
  },
  setupDefaultEnvironment: We("setupDefaultEnvironment"),
  cloneEnvironment: We("cloneEnvironment"),
  getRepos: async () => [],
  createRepo: We("createRepo"),
  updateRepo: We("updateRepo"),
  deleteRepo: async () => {
  },
  syncRepo: async () => ({ status: "unsupported", message: "", task_id: null }),
  getBranches: async () => [],
  checkoutBranch: We("checkoutBranch"),
  getProjectMembers: async () => [],
  addProjectMember: We("addProjectMember"),
  updateProjectMember: We("updateProjectMember"),
  removeProjectMember: async () => {
  },
  login: We("login"),
  getMe: We("getMe"),
  patchFirstLoginComplete: We("patchFirstLoginComplete"),
  getFeatures: async () => ({ flags: {}, locked: {} }),
  startDebugSession: We("startDebugSession"),
  getDebugState: async (e) => ({
    session_id: e,
    paused: !1,
    terminated: !0,
    paused_at: { file: null, line: null, keyword: null },
    scopes: [],
    call_stack: [],
    output_lines: []
  }),
  postDebugControl: async () => {
  },
  installDebugPrerequisites: async () => ({ already_installed: !1, log_tail: null })
};
let _n = Ma;
function V_(e) {
  _n = { ..._n, ...e };
}
function A_() {
  _n = Ma;
}
function ye() {
  return _n;
}
const ml = (e, n = "") => ye().getTree(e, n), xa = (e, n, r) => ye().getFile(e, n, r), Ua = (e, n, r) => ye().saveFile(e, n, r), gl = (e, n, r = "") => ye().createFile(e, n, r), vl = (e, n) => ye().deleteFile(e, n), hl = (e, n, r) => ye().renameFile(e, n, r), yl = (e, n, r) => ye().search(e, n, r), bl = (e) => ye().getTestCases(e);
async function wl(e, n) {
}
async function _l(e, n) {
}
async function kl(e) {
  return [];
}
async function Oo(e, n) {
  return ye().searchKeywords(e, n);
}
async function Cl(e) {
  return ye().invalidateKeywordCache(e);
}
const $l = () => ye().getEnvironments(), Tl = (e) => ye().getPackages(e), El = (e) => ye().getInstalledPackages(e), Ba = (e, n) => ye().installPackage(e, n), Sl = (e, n) => ye().uninstallPackage(e, n), Ll = (e) => ye().getVariables(e), Vl = (e, n) => ye().createVariable(e, n), Al = (e, n, r) => ye().updateVariable(e, n, r), Nl = (e, n) => ye().deleteVariable(e, n), Rl = (e) => ye().createEnvironment(e), Fl = (e) => ye().deleteEnvironment(e), Pl = () => ye().setupDefaultEnvironment(), Dl = (e, n) => ye().cloneEnvironment(e, n);
async function Ml(e) {
  return ye().getEnvironmentKeywords(e);
}
const xl = () => ye().getRepos(), Ul = (e) => ye().createRepo(e), Bl = (e, n) => ye().updateRepo(e, n), Kl = (e) => ye().deleteRepo(e), Hl = (e) => ye().syncRepo(e), Ol = (e) => ye().getBranches(e), Il = (e, n) => ye().checkoutBranch(e, n), Wl = (e) => ye().getProjectMembers(e), jl = (e, n, r) => ye().addProjectMember(e, n, r), ql = (e, n, r) => ye().updateProjectMember(e, n, r), zl = (e, n) => ye().removeProjectMember(e, n), Ka = $o("repos", () => {
  const e = N([]), n = N(!1), r = N({}), c = N({});
  async function g() {
    n.value = !0;
    try {
      e.value = await xl();
    } finally {
      n.value = !1;
    }
  }
  async function a(M) {
    const J = await Ul(M);
    return e.value.push(J), J;
  }
  async function _(M) {
    return await Hl(M);
  }
  async function y(M, J) {
    const ae = await Bl(M, J), z = e.value.findIndex((D) => D.id === M);
    return z >= 0 && (e.value[z] = ae), ae;
  }
  async function k(M) {
    await Kl(M), e.value = e.value.filter((J) => J.id !== M);
  }
  async function A(M) {
    r.value[M] = await Ol(M);
  }
  async function V(M, J) {
    await Il(M, J);
    const ae = e.value.find((z) => z.id === M);
    ae && (ae.default_branch = J), r.value[M] && (r.value[M] = r.value[M].map((z) => ({
      ...z,
      is_active: z.name === J
    })));
  }
  function E(M) {
    return e.value.find((J) => J.id === M);
  }
  async function B(M) {
    c.value[M] = await Wl(M);
  }
  async function I(M, J, ae) {
    const z = await jl(M, J, ae);
    c.value[M] || (c.value[M] = []);
    const D = c.value[M].findIndex((R) => R.user_id === J);
    return D >= 0 ? c.value[M][D] = z : c.value[M].push(z), z;
  }
  async function j(M, J, ae) {
    var R;
    const z = await ql(M, J, ae), D = (R = c.value[M]) == null ? void 0 : R.findIndex((H) => H.id === J);
    return D !== void 0 && D >= 0 && (c.value[M][D] = z), z;
  }
  async function ue(M, J) {
    await zl(M, J), c.value[M] && (c.value[M] = c.value[M].filter((ae) => ae.id !== J));
  }
  return { repos: e, loading: n, branches: r, members: c, fetchRepos: g, addRepo: a, updateRepo: y, syncRepo: _, removeRepo: k, fetchBranches: A, checkoutBranch: V, getRepo: E, fetchMembers: B, addMember: I, updateMember: j, removeMember: ue };
}), oa = $o("environments", () => {
  const e = N([]), n = N({}), r = N({}), c = N({}), g = N(!1);
  async function a() {
    g.value = !0;
    try {
      e.value = await $l();
    } finally {
      g.value = !1;
    }
  }
  async function _(D) {
    const R = await Rl(D);
    return e.value.push(R), R;
  }
  async function y(D) {
    await Fl(D), e.value = e.value.filter((R) => R.id !== D);
  }
  async function k() {
    const D = await Pl();
    return e.value.push(D), D;
  }
  async function A(D, R) {
    const H = await Dl(D, R);
    return e.value.push(H), H;
  }
  async function V(D) {
    const R = await Tl(D), H = /* @__PURE__ */ new Set();
    n.value[D] = R.filter((Y) => H.has(Y.package_name) ? !1 : (H.add(Y.package_name), !0));
  }
  async function E(D, R) {
    const H = await Ba(D, R);
    n.value[D] || (n.value[D] = []), n.value[D].some((Y) => Y.package_name === H.package_name) || n.value[D].push(H);
  }
  async function B(D, R) {
    await Sl(D, R), n.value[D] && (n.value[D] = n.value[D].filter((H) => H.package_name !== R));
  }
  async function I(D) {
    r.value[D] = await Ll(D);
  }
  async function j(D, R) {
    await Vl(D, R), await I(D);
  }
  async function ue(D, R, H) {
    await Al(D, R, H), await I(D);
  }
  async function M(D, R) {
    await Nl(D, R), r.value[D] = (r.value[D] || []).filter((H) => H.id !== R);
  }
  function J(D, R, H, Y) {
    const U = n.value[D];
    if (!U) return;
    const Z = U.findIndex((ce) => ce.package_name === R);
    Z < 0 || (U[Z] = {
      ...U[Z],
      install_status: H,
      install_error: Y.error || null,
      installed_version: Y.installed_version || U[Z].installed_version
    });
  }
  function ae(D, R, H) {
    c.value[D] || (c.value[D] = []), R && c.value[D].push(R), H && a();
  }
  function z(D) {
    delete c.value[D];
  }
  return {
    environments: e,
    packages: n,
    variables: r,
    buildLogs: c,
    loading: g,
    fetchEnvironments: a,
    addEnvironment: _,
    setupDefault: k,
    removeEnvironment: y,
    cloneEnvironment: A,
    fetchPackages: V,
    installPackage: E,
    uninstallPackage: B,
    fetchVariables: I,
    updatePackageFromWs: J,
    createVariable: j,
    updateVariable: ue,
    deleteVariable: M,
    appendBuildLog: ae,
    clearBuildLogs: z
  };
}), zo = $o("explorer", () => {
  const e = N(null), n = N(null), r = N([]), c = N([]), g = N(!1), a = N(null), _ = N([]), y = N(!1), k = N(!1), A = N(null);
  let V = null;
  const E = N([]);
  function B(ee) {
    E.value = ee;
  }
  async function I(ee, W = "") {
    g.value = !0, a.value = ee;
    try {
      e.value = await ml(ee, W);
    } finally {
      g.value = !1;
    }
  }
  async function j(ee, W, oe) {
    g.value = !0;
    try {
      n.value = await xa(ee, W, oe);
    } finally {
      g.value = !1;
    }
  }
  async function ue(ee, W, oe) {
    g.value = !0;
    try {
      r.value = await yl(ee, W, oe);
    } finally {
      g.value = !1;
    }
  }
  async function M(ee) {
    g.value = !0;
    try {
      c.value = await bl(ee);
    } finally {
      g.value = !1;
    }
  }
  async function J(ee, W, oe) {
    const fe = await Ua(ee, W, oe);
    return n.value = fe, fe;
  }
  async function ae(ee, W, oe = "") {
    const fe = await gl(ee, W, oe);
    return await I(ee), fe;
  }
  async function z(ee, W) {
    var oe;
    await vl(ee, W), ((oe = n.value) == null ? void 0 : oe.path) === W && (n.value = null), await I(ee);
  }
  async function D(ee, W, oe) {
    var be;
    const fe = await hl(ee, W, oe);
    return ((be = n.value) == null ? void 0 : be.path) === W && (n.value = fe), await I(ee), fe;
  }
  async function R(ee, W) {
    await wl();
  }
  async function H(ee, W) {
    await _l();
  }
  function Y() {
    n.value = null, r.value = [];
  }
  function U(ee) {
    try {
      const W = Ka().getRepo(ee);
      if ((W == null ? void 0 : W.environment_id) != null) return W.environment_id;
      const oe = oa().environments.find((fe) => fe.is_default);
      return (oe == null ? void 0 : oe.id) ?? null;
    } catch {
      return null;
    }
  }
  async function Z(ee) {
    if (!(k.value && A.value === ee)) {
      if (y.value) {
        V = ee;
        return;
      }
      y.value = !0, k.value = !1;
      try {
        const W = U(ee);
        if (W != null)
          try {
            const fe = await Ml(W);
            if (fe.keywords && fe.keywords.length > 0) {
              _.value = fe.keywords.map((be) => ({
                name: be.name,
                library: be.library,
                doc: be.shortdoc || "",
                doc_format: "text",
                args: be.args || []
              })), k.value = !0, A.value = ee;
              return;
            }
          } catch {
          }
        const oe = await Oo("*", ee);
        _.value = (oe.results || []).map((fe) => ({
          name: fe.name,
          library: fe.library,
          doc: fe.doc || "",
          doc_format: fe.doc_format || "text",
          args: fe.args || []
        })), k.value = !0, A.value = ee;
      } catch {
        _.value = [], A.value = null;
      } finally {
        if (y.value = !1, V !== null && V !== A.value) {
          const W = V;
          V = null, Z(W);
        } else
          V = null;
      }
    }
  }
  async function ce(ee) {
    try {
      await Cl(ee);
    } catch {
    }
    k.value = !1, await Z(ee);
  }
  function ke() {
    e.value = null, n.value = null, r.value = [], c.value = [], _.value = [], k.value = !1, A.value = null, E.value = [];
  }
  return {
    tree: e,
    selectedFile: n,
    searchResults: r,
    testCases: c,
    loading: g,
    currentscopeId: a,
    keywords: _,
    keywordsLoading: y,
    keywordsLoaded: k,
    keywordsscopeId: A,
    projectKeywords: E,
    fetchTree: I,
    openFile: j,
    searchInRepo: ue,
    fetchTestCases: M,
    clearSelection: Y,
    clearAll: ke,
    saveFile: J,
    createFile: ae,
    deleteFileAction: z,
    renameFileAction: D,
    openInEditorAction: R,
    openInFileBrowserAction: H,
    preloadKeywords: Z,
    refreshKeywords: ce,
    setProjectKeywords: B,
    resolveEnvironmentId: U
  };
});
function Tn() {
  const e = zo(), n = O(() => {
    const _ = /* @__PURE__ */ new Map();
    for (const [y, k] of _o) _.set(y.toLowerCase(), k);
    for (const y of e.keywords)
      y.args && y.args.length && _.set(y.name.toLowerCase(), y.args);
    for (const y of e.projectKeywords)
      y.name && _.set(y.name.toLowerCase(), y.arguments ?? []);
    return _;
  });
  function r(_) {
    if (!_) return null;
    const y = n.value.get(_.toLowerCase());
    if (y) return y;
    const k = Aa(_);
    return k ? n.value.get(k.rest.toLowerCase()) ?? null : null;
  }
  function c(_) {
    const y = r(_);
    return y ? y.map(ta) : null;
  }
  function g(_) {
    if (!_) return null;
    const y = _.toLowerCase(), k = n.value.get(y);
    if (!k) return null;
    const A = e.keywords.find((E) => E.name.toLowerCase() === y), V = e.projectKeywords.find((E) => E.name.toLowerCase() === y);
    return V ? {
      display: V.name,
      library: V.file_path,
      doc: V.doc ?? "",
      docFormat: "text",
      args: k
    } : {
      display: (A == null ? void 0 : A.name) ?? _,
      library: (A == null ? void 0 : A.library) ?? "BuiltIn",
      doc: (A == null ? void 0 : A.doc) ?? "",
      docFormat: (A == null ? void 0 : A.doc_format) ?? "text",
      args: k
    };
  }
  async function a(_, y) {
    if (!_) return null;
    try {
      const k = await Oo(_, y), A = _.toLowerCase(), V = k.results.find((B) => B.name.toLowerCase() === A) ?? k.results[0];
      return V ? (e.keywords.some((B) => B.name.toLowerCase() === V.name.toLowerCase()) || e.keywords.push({
        name: V.name,
        library: V.library || "",
        doc: V.doc || "",
        doc_format: V.doc_format || "text",
        args: V.args || []
      }), {
        display: V.name,
        library: V.library || "BuiltIn",
        doc: V.doc || "",
        docFormat: V.doc_format || "text",
        args: V.args ?? r(_) ?? []
      }) : g(_);
    } catch {
      return g(_);
    }
  }
  return { argsByName: n, getArgs: r, getParsedArgs: c, getKeywordInfo: g, fetchKeywordInfo: a };
}
function Gl(e, n) {
  const r = (E) => E.replace(/\\/g, "/").replace(/^\.\//, ""), c = r(e), g = r(n);
  if (!c) return g;
  const a = c.split("/").slice(0, -1), _ = g.split("/");
  let y = 0;
  for (; y < a.length && y < _.length - 1 && a[y] === _[y]; )
    y++;
  const k = a.length - y, A = _.slice(y);
  return [...Array(k).fill(".."), ...A].join("/") || g;
}
const Yl = /\.(resource|robot|txt|tsv)$/i;
function Xl(e) {
  const n = e.trim();
  return Yl.test(n) || n.includes("/");
}
const Wn = "robot-autotest.flowPalette.filter", fa = "robot-autotest.flowPalette.sort", Zl = 1, Ql = 5;
function Jl(e) {
  return e.importCount >= Zl || e.stepCount >= Ql;
}
const er = {
  resources: !0,
  importedLibs: !0,
  exampleLibs: !0,
  builtin: !0
}, tr = {
  resources: !0,
  importedLibs: !0,
  exampleLibs: !1,
  // hide the not-installed example noise
  builtin: !0
};
function or(e) {
  return e.hasEnvData && Jl(e.file) ? { ...tr } : { ...er };
}
function nr(e) {
  if (!e) return null;
  try {
    const n = JSON.parse(e);
    if (typeof (n == null ? void 0 : n.resources) == "boolean" && typeof (n == null ? void 0 : n.importedLibs) == "boolean" && typeof (n == null ? void 0 : n.exampleLibs) == "boolean" && typeof (n == null ? void 0 : n.builtin) == "boolean")
      return { resources: n.resources, importedLibs: n.importedLibs, exampleLibs: n.exampleLibs, builtin: n.builtin };
  } catch {
  }
  return null;
}
function ar(e) {
  return e === "mostUsed" || e === "alpha" || e === "importedFirst" ? e : null;
}
function sr(e) {
  const n = /* @__PURE__ */ new Set();
  for (const r of e) {
    const g = (r.split("/").pop() || r).trim().replace(/\.[^.]+$/, "");
    g && n.add(g.toLowerCase());
  }
  return n;
}
function Ha(e) {
  return e.kind === "resource" ? "resources" : e.kind === "control" ? null : e.name === "BuiltIn" ? "builtin" : e.isExamples ? "exampleLibs" : "importedLibs";
}
function lr(e, n) {
  return e.filter((r) => {
    const c = Ha(r);
    return c === null ? !0 : n[c];
  });
}
function rr(e, n) {
  return e.reduce((r, c) => {
    const g = Ha(c);
    return g !== null && !n[g] ? r + 1 : r;
  }, 0);
}
function ir(e, n, r) {
  const c = [...e];
  return n === "alpha" ? c.sort((g, a) => g.name.localeCompare(a.name, void 0, { sensitivity: "base" })) : n === "importedFirst" ? c.sort((g, a) => {
    const _ = g.isExamples ? 1 : 0, y = a.isExamples ? 1 : 0;
    return _ !== y ? _ - y : (r.get(a.name) ?? 0) - (r.get(g.name) ?? 0);
  }) : c.sort((g, a) => (r.get(a.name) ?? 0) - (r.get(g.name) ?? 0)), c;
}
function ma(e, n, r) {
  const c = (e || "").trim();
  if (!c) return "";
  if (n !== "html") return c;
  const g = c.replace(/<br\s*\/?>/gi, `
`).replace(/<\/(p|div|li|h[1-6]|tr|pre|blockquote)\s*>/gi, `
`);
  return r(g).replace(/[ \t]+/g, " ").replace(/[ \t]*\n[ \t]*/g, `
`).replace(/\n{3,}/g, `

`).trim();
}
const ur = /^[A-Za-z_\u4e00-\u9fa5][\w\u4e00-\u9fa5]*$/;
function cr(e) {
  const n = [];
  for (const r of e || []) {
    const c = (r || "").trim();
    if (c && !c.includes("=")) {
      if (c.startsWith("**")) {
        n.push(`&{${c.slice(2).replace(/_$/, "")}}`);
        continue;
      }
      if (c.startsWith("*")) {
        n.push(`@{${c.slice(1).replace(/_$/, "")}}`);
        continue;
      }
      if (c.startsWith("${") || c.startsWith("@{") || c.startsWith("&{") || c.startsWith("%{")) {
        n.push(c);
        continue;
      }
      ur.test(c) && n.push(`\${${c.replace(/_$/, "")}}`);
    }
  }
  return n;
}
const dr = {
  "should be equal": ["actual", "expected", "msg=None", "values=True", "formatter=str"],
  "should contain": ["container", "item", "msg=None", "values=True"],
  "should not be empty": ["item", "msg=None"],
  "should be true": ["condition", "msg=None"],
  log: ["message", "level=INFO", "html=False", "console=False"],
  "log to console": ["message", "stream=STDOUT", "no_newline=False"],
  sleep: ["time", "reason=None"],
  "set variable": ["*values"],
  "run keyword if": ["condition", "name", "*args"],
  evaluate: ["expression", "modules=None", "namespace=None"],
  "create list": ["*items"],
  "create dictionary": ["*items"],
  "append to list": ["list_", "*values"],
  "open browser": ["url", "browser=firefox"],
  "click element": ["locator"],
  "input text": ["locator", "text"]
};
function pr(e, n) {
  const r = n(e);
  return r && r.length > 0 ? r : dr[e.toLowerCase()] ?? [];
}
const fr = { class: "keyword-palette" }, mr = { class: "palette-header" }, gr = { class: "palette-actions" }, vr = { class: "palette-menu-wrap" }, hr = ["title"], yr = {
  key: 0,
  class: "palette-menu",
  "data-testid": "palette-sort-menu"
}, br = ["onClick"], wr = { class: "palette-menu-check" }, _r = { class: "palette-menu-wrap" }, kr = ["title"], Cr = {
  key: 0,
  class: "palette-menu palette-menu--wide",
  "data-testid": "palette-filter-menu"
}, $r = ["checked", "onChange"], Tr = ["placeholder"], Er = {
  key: 0,
  class: "palette-offline",
  "data-testid": "palette-offline"
}, Sr = {
  key: 1,
  class: "palette-hiding-hint",
  "data-testid": "palette-hiding-hint"
}, Lr = {
  key: 2,
  class: "palette-add-bar"
}, Vr = { class: "palette-add-info" }, Ar = ["title"], Nr = ["title"], Rr = {
  key: 1,
  class: "palette-args-preview"
}, Fr = {
  key: 2,
  class: "palette-add-doc",
  "data-testid": "palette-add-doc"
}, Pr = {
  key: 0,
  class: "palette-add-doc-muted"
}, Dr = ["title"], Mr = {
  key: 2,
  class: "palette-add-doc-muted"
}, xr = { class: "palette-categories" }, Ur = {
  class: "palette-section-label",
  "data-testid": "palette-resources-label"
}, Br = ["title"], Kr = ["onClick"], Hr = { class: "collapse-icon" }, Or = { class: "category-file-glyph" }, Ir = { class: "category-file" }, Wr = ["title"], jr = {
  key: 0,
  class: "category-file-path"
}, qr = ["title"], zr = { class: "category-count" }, Gr = ["title", "onDragstart", "onClick", "onDblclick"], Yr = { class: "palette-item-content" }, Xr = { class: "palette-item-name" }, Zr = {
  key: 0,
  class: "palette-item-argcount"
}, Qr = ["onClick"], Jr = { class: "collapse-icon" }, ei = { class: "category-name" }, ti = ["title"], oi = { class: "category-count" }, ni = ["title", "onDragstart", "onClick", "onDblclick"], ai = { class: "palette-item-content" }, si = { class: "palette-item-name" }, li = {
  key: 0,
  class: "palette-item-argcount"
}, ri = ["title"], ii = ["title", "onDragstart", "onClick", "onDblclick"], ui = { class: "palette-item-name" }, ci = {
  key: 1,
  class: "palette-empty"
}, di = /* @__PURE__ */ je({
  __name: "KeywordPalette",
  props: {
    scopeId: {},
    filePath: {},
    importedLibraries: {},
    usageCounts: {},
    fileImportCount: {},
    fileStepCount: {}
  },
  emits: ["add-node"],
  setup(e, { emit: n }) {
    const r = e, { t: c } = mt(), g = zo(), a = n;
    function _(F) {
      return F.kind === "control" || F.kind === "resource" || F.name === "BuiltIn" || !r.importedLibraries ? !0 : r.importedLibraries.has(F.name.toLowerCase());
    }
    const y = N(/* @__PURE__ */ new Map()), k = N([]), A = N(!1), V = N(!1), E = N(/* @__PURE__ */ new Map());
    function B() {
      const F = /* @__PURE__ */ new Map(), w = /* @__PURE__ */ new Map();
      for (const P of g.keywords) {
        const G = P.library || "Unknown";
        w.has(G) || w.set(G, []), w.get(G).push({ name: P.name, library: P.library, doc: P.doc, args: P.args || [] }), P.args && P.args.length > 0 && F.set(P.name, P.args);
      }
      y.value = w, E.value = F;
    }
    async function I() {
      if (r.scopeId) {
        A.value = !0, V.value = !1;
        try {
          if (g.keywordsLoaded && g.keywords.length > 0)
            B();
          else {
            const w = await Oo("*", r.scopeId), P = /* @__PURE__ */ new Map(), G = /* @__PURE__ */ new Map();
            for (const ge of w.results) {
              const Ve = ge.library || "Unknown";
              G.has(Ve) || G.set(Ve, []), G.get(Ve).push(ge), ge.args && ge.args.length > 0 && P.set(ge.name, ge.args);
            }
            y.value = G, E.value = P;
          }
          const F = await kl(r.scopeId).catch(() => []);
          k.value = F, g.setProjectKeywords(F);
          for (const w of F)
            w.arguments && w.arguments.length > 0 && E.value.set(w.name, w.arguments);
        } catch {
          V.value = !0, y.value = /* @__PURE__ */ new Map(), k.value = [], E.value = /* @__PURE__ */ new Map();
        } finally {
          A.value = !1;
        }
      }
    }
    qo(() => I()), Le(() => r.scopeId, () => I()), Le(() => g.keywordsLoaded, (F) => {
      F && g.keywords.length > 0 && B();
    });
    const j = N(""), ue = N(/* @__PURE__ */ new Set()), M = N(null);
    function J(F) {
      ue.value.has(F) ? ue.value.delete(F) : ue.value.add(F);
    }
    function ae(F) {
      return j.value ? !0 : ue.value.has(F);
    }
    function z() {
      for (const F of ht.value)
        ue.value.add(F.name);
    }
    function D() {
      ue.value.clear();
    }
    function R(F) {
      return pr(F, (w) => E.value.get(w) || []);
    }
    const H = O(() => {
      const F = /* @__PURE__ */ new Map();
      for (const w of k.value) F.set(w.name, w.file_path);
      return F;
    });
    function Y(F, w) {
      if (F.kind === "resource") {
        const P = H.value.get(w);
        return P && P !== r.filePath ? Gl(r.filePath || "", P) : void 0;
      }
      if (F.kind !== "control" && F.name !== "BuiltIn")
        return F.name;
    }
    const { getKeywordInfo: U, fetchKeywordInfo: Z } = Tn(), ce = N(/* @__PURE__ */ new Map()), ke = N(!1);
    let ee = 0;
    function W(F) {
      return Va.sanitize(F, { ALLOWED_TAGS: [], ALLOWED_ATTR: [] });
    }
    function oe(F) {
      return g.projectKeywords.some((w) => w.name.toLowerCase() === F);
    }
    const fe = O(() => {
      const F = M.value;
      return !F || F.type ? null : U(F.name);
    }), be = O(() => {
      var F;
      return ((F = fe.value) == null ? void 0 : F.library) ?? "";
    }), Fe = O(() => {
      const F = M.value;
      if (!F || F.type) return "";
      const w = fe.value;
      if (w != null && w.doc && w.doc.trim()) return ma(w.doc, w.docFormat, W);
      const P = ce.value.get(F.name.toLowerCase());
      return P ? ma(P.doc, P.docFormat, W) : "";
    });
    Le(M, async (F) => {
      const w = ++ee;
      if (ke.value = !1, !F || F.type) return;
      const P = F.name.toLowerCase();
      if (ce.value.has(P) || oe(P)) return;
      const G = U(F.name);
      if (!(G != null && G.doc && G.doc.trim())) {
        ke.value = !0;
        try {
          const ge = await Z(F.name, r.scopeId);
          if (w !== ee) return;
          ge && ge.display.toLowerCase() === P && (ce.value.set(P, { doc: ge.doc, docFormat: ge.docFormat }), ce.value = new Map(ce.value));
        } finally {
          w === ee && (ke.value = !1);
        }
      }
    });
    function Pe(F, w, P) {
      M.value = { name: F, type: w, library: P };
    }
    function He(F) {
      var w;
      return ((w = M.value) == null ? void 0 : w.name) === F;
    }
    function ie() {
      M.value && (M.value.type ? Nt(M.value.type) : At(M.value.name, M.value.library), M.value = null);
    }
    const Ie = [
      {
        name: "BuiltIn",
        keywords: [
          "Log",
          "Log To Console",
          "Set Variable",
          "Should Be Equal",
          "Should Contain",
          "Should Not Be Empty",
          "Should Be True",
          "Sleep",
          "Run Keyword If",
          "Run Keyword And Return Status",
          "Fail",
          "Pass Execution",
          "Set Test Variable",
          "Set Suite Variable",
          "Set Global Variable",
          "Get Length",
          "Convert To String",
          "Convert To Integer",
          "Evaluate",
          "Catenate"
        ]
      },
      {
        name: "Collections",
        keywords: [
          "Create List",
          "Create Dictionary",
          "Append To List",
          "Get From Dictionary",
          "Set To Dictionary",
          "List Should Contain Value",
          "Dictionary Should Contain Key",
          "Get Length",
          "Remove From List"
        ]
      },
      {
        name: "String",
        keywords: [
          "Replace String",
          "Split String",
          "Get Substring",
          "Should Match Regexp",
          "Convert To Lower Case",
          "Convert To Upper Case",
          "Strip String",
          "Get Line Count"
        ]
      },
      {
        name: "DateTime",
        keywords: [
          "Get Current Date",
          "Convert Date",
          "Convert Time",
          "Subtract Date From Date",
          "Add Time To Date",
          "Get Time"
        ]
      },
      {
        name: "OperatingSystem",
        keywords: [
          "Run",
          "Run And Return Rc",
          "File Should Exist",
          "Directory Should Exist",
          "Create File",
          "Remove File",
          "Copy File",
          "Move File",
          "List Directory",
          "Get Environment Variable"
        ]
      },
      {
        name: "Process",
        keywords: [
          "Run Process",
          "Start Process",
          "Wait For Process",
          "Terminate Process",
          "Get Process Result",
          "Process Should Be Running"
        ]
      },
      {
        name: "XML",
        keywords: [
          "Parse XML",
          "Get Element",
          "Get Element Text",
          "Get Element Attribute",
          "Element Should Exist"
        ]
      },
      {
        name: "Browser",
        keywords: [
          "New Browser",
          "New Page",
          "Click",
          "Fill Text",
          "Get Text",
          "Wait For Elements State",
          "Take Screenshot",
          "Go To",
          "Get Url",
          "Close Browser"
        ]
      },
      {
        name: "SeleniumLibrary",
        keywords: [
          "Open Browser",
          "Close Browser",
          "Click Element",
          "Input Text",
          "Get Text",
          "Wait Until Element Is Visible",
          "Page Should Contain",
          "Capture Page Screenshot",
          "Get Title",
          "Go To"
        ]
      },
      {
        name: "RequestsLibrary",
        keywords: [
          "Create Session",
          "GET",
          "POST",
          "PUT",
          "DELETE",
          "PATCH",
          "Status Should Be",
          "Should Be Equal As Strings"
        ]
      },
      {
        name: "DatabaseLibrary",
        keywords: [
          "Connect To Database",
          "Disconnect From Database",
          "Execute Sql String",
          "Query",
          "Row Count Is Equal To",
          "Check If Exists In Database"
        ]
      }
    ], tt = {
      name: "Control",
      kind: "control",
      items: [
        { label: "IF / ELSE", type: "if" },
        { label: "ELSE IF", type: "else_if" },
        { label: "ELSE", type: "else" },
        { label: "FOR Loop", type: "for" },
        { label: "WHILE Loop", type: "while" },
        { label: "TRY / EXCEPT", type: "try" },
        { label: "EXCEPT", type: "except" },
        { label: "FINALLY", type: "finally" },
        { label: "VAR", type: "var" },
        { label: "RETURN", type: "return" },
        { label: "BREAK", type: "break" },
        { label: "CONTINUE", type: "continue" },
        { label: "Comment", type: "comment" }
      ]
    }, gt = [
      "BuiltIn",
      "Collections",
      "String",
      "DateTime",
      "OperatingSystem",
      "Process",
      "XML",
      "Browser",
      "SeleniumLibrary",
      "RequestsLibrary",
      "DatabaseLibrary"
    ], ut = O(() => {
      if (k.value.length === 0) return [];
      const F = /* @__PURE__ */ new Map();
      for (const P of k.value) {
        const G = F.get(P.file_path) ?? [];
        G.push(P.name), F.set(P.file_path, G);
      }
      const w = [];
      for (const [P, G] of F) {
        const ge = P.lastIndexOf("/"), Ve = ge >= 0 ? P.slice(ge + 1) : P, nt = ge >= 0 ? P.slice(0, ge) : "";
        w.push({
          name: Ve,
          keywords: G,
          kind: "resource",
          relPath: nt,
          isResourceFile: Ve.toLowerCase().endsWith(".resource"),
          isCurrentFile: P === r.filePath
        });
      }
      return w.sort((P, G) => {
        const ge = Number(G.isCurrentFile) - Number(P.isCurrentFile);
        return ge !== 0 ? ge : P.name.localeCompare(G.name, void 0, { sensitivity: "base" });
      }), w;
    }), Ze = O(() => {
      var G, ge;
      const F = [], w = /* @__PURE__ */ new Set(), P = sr(k.value.map((Ve) => Ve.file_path));
      for (const [Ve, nt] of y.value)
        P.has(Ve.toLowerCase()) || (F.push({
          name: Ve,
          keywords: nt.map((Ht) => Ht.name),
          kind: "library",
          imported: ((G = r.importedLibraries) == null ? void 0 : G.has(Ve.toLowerCase())) ?? !1
        }), w.add(Ve));
      for (const Ve of gt) {
        if (w.has(Ve)) continue;
        const nt = Ie.find((Ht) => Ht.name === Ve);
        nt && F.push({
          name: nt.name,
          keywords: nt.keywords,
          kind: "library",
          isExamples: Ve !== "BuiltIn",
          imported: ((ge = r.importedLibraries) == null ? void 0 : ge.has(Ve.toLowerCase())) ?? Ve === "BuiltIn"
        });
      }
      return F;
    }), vt = O(() => y.value.size > 0), ze = N(
      ar(localStorage.getItem(fa)) ?? "mostUsed"
    ), Q = N(!1);
    function ot(F) {
      ze.value = F, localStorage.setItem(fa, F), Q.value = !1;
    }
    const pe = N(
      nr(localStorage.getItem(Wn))
    ), se = N(!1), me = O(
      () => pe.value ?? or({
        hasEnvData: vt.value,
        file: { importCount: r.fileImportCount ?? 0, stepCount: r.fileStepCount ?? 0 }
      })
    );
    function Se(F) {
      const w = { ...me.value, [F]: !me.value[F] };
      pe.value = w, localStorage.setItem(Wn, JSON.stringify(w));
    }
    function Be() {
      const F = { resources: !0, importedLibs: !0, exampleLibs: !0, builtin: !0 };
      pe.value = F, localStorage.setItem(Wn, JSON.stringify(F));
    }
    const ht = O(() => {
      const F = r.usageCounts ?? /* @__PURE__ */ new Map(), w = ir(Ze.value, ze.value, F);
      return [...ze.value === "alpha" ? [...ut.value].sort((G, ge) => G.name.localeCompare(ge.name, void 0, { sensitivity: "base" })) : ut.value, ...w, tt];
    }), Bt = O(
      () => lr(ht.value, me.value)
    ), Vt = O(
      () => rr(ht.value, me.value)
    );
    function yt() {
      ue.value.clear();
    }
    Le(() => r.filePath, (F, w) => {
      !F || F === w || yt();
    }), Le(() => g.keywordsLoaded, (F, w) => {
      F && w === !1 && yt();
    });
    const to = O(() => {
      const F = j.value.toLowerCase();
      return F ? Bt.value.map((w) => {
        if ("keywords" in w) {
          const G = w.keywords.filter((ge) => ge.toLowerCase().includes(F));
          return G.length ? { ...w, keywords: G } : null;
        }
        const P = w.items.filter((G) => G.label.toLowerCase().includes(F));
        return P.length ? { ...w, items: P } : null;
      }).filter((w) => w !== null) : Bt.value;
    }), bt = O(
      () => to.value.filter((F) => F.kind === "resource")
    ), oo = O(
      () => to.value.filter((F) => F.kind !== "resource")
    );
    function Kt(F = "keyword") {
      return {
        type: F,
        keyword: "",
        args: [],
        returnVars: [],
        condition: "",
        loopVar: "${item}",
        loopFlavor: "IN",
        loopValues: [],
        exceptPattern: "",
        exceptVar: "",
        varScope: "",
        comment: ""
      };
    }
    function At(F, w) {
      const P = Kt("keyword");
      P.keyword = F, P.args = cr(R(F)), a("add-node", P, w);
    }
    function Nt(F) {
      const w = Kt(F);
      F === "if" && (w.condition = "${condition}"), F === "for" && (w.loopVar = "${item}", w.loopFlavor = "IN", w.loopValues = ["@{list}"]), F === "while" && (w.condition = "${condition}"), a("add-node", w);
    }
    function wt(F, w, P) {
      var G, ge;
      (G = F.dataTransfer) == null || G.setData("application/rf-keyword", w), P && ((ge = F.dataTransfer) == null || ge.setData("application/rf-library", P)), F.dataTransfer.effectAllowed = "copy";
    }
    function _t(F, w) {
      var P;
      (P = F.dataTransfer) == null || P.setData("application/rf-control", w), F.dataTransfer.effectAllowed = "copy";
    }
    return (F, w) => (i(), u("div", fr, [
      t("div", mr, [
        t("h4", null, m(p(c)("flowEditor.palette") || "Keywords"), 1),
        t("div", gr, [
          t("div", vr, [
            t("button", {
              class: "palette-action-btn",
              title: p(c)("flowEditor.sort.title"),
              "data-testid": "palette-sort-btn",
              onClick: w[0] || (w[0] = (P) => {
                Q.value = !Q.value, se.value = !1;
              })
            }, "⇅", 8, hr),
            Q.value ? (i(), u("div", yr, [
              (i(), u(x, null, X(["mostUsed", "alpha", "importedFirst"], (P) => t("button", {
                key: P,
                class: ne(["palette-menu-item", { "palette-menu-item--active": ze.value === P }]),
                onClick: (G) => ot(P)
              }, [
                t("span", wr, m(ze.value === P ? "✓" : ""), 1),
                we(" " + m(p(c)(`flowEditor.sort.${P}`)), 1)
              ], 10, br)), 64))
            ])) : T("", !0)
          ]),
          t("div", _r, [
            t("button", {
              class: ne(["palette-action-btn", { "palette-action-btn--on": Vt.value > 0 }]),
              title: p(c)("flowEditor.filter.title"),
              "data-testid": "palette-filter-btn",
              onClick: w[1] || (w[1] = (P) => {
                se.value = !se.value, Q.value = !1;
              })
            }, "▼", 10, kr),
            se.value ? (i(), u("div", Cr, [
              (i(), u(x, null, X(["resources", "importedLibs", "exampleLibs", "builtin"], (P) => t("label", {
                key: P,
                class: "palette-menu-item palette-menu-item--check"
              }, [
                t("input", {
                  type: "checkbox",
                  checked: me.value[P],
                  onChange: (G) => Se(P)
                }, null, 40, $r),
                we(" " + m(p(c)(`flowEditor.filter.${P}`)), 1)
              ])), 64))
            ])) : T("", !0)
          ]),
          t("button", {
            class: "palette-action-btn",
            onClick: z,
            title: "Expand all"
          }, "⊞"),
          t("button", {
            class: "palette-action-btn",
            onClick: D,
            title: "Collapse all"
          }, "⊟")
        ])
      ]),
      q(t("input", {
        "onUpdate:modelValue": w[2] || (w[2] = (P) => j.value = P),
        class: "palette-search",
        placeholder: p(c)("flowEditor.searchKeywords") || "Search keywords..."
      }, null, 8, Tr), [
        [le, j.value]
      ]),
      V.value ? (i(), u("div", Er, [...w[3] || (w[3] = [
        t("span", { class: "palette-offline-title" }, "暂无数据", -1),
        t("span", { class: "palette-offline-hint" }, "平台关键词接口不可用，已回退内置示例", -1)
      ])])) : T("", !0),
      Vt.value > 0 && !j.value ? (i(), u("div", Sr, [
        t("span", null, m(p(c)("flowEditor.filter.hidingCategories", { count: Vt.value })), 1),
        t("button", {
          class: "palette-hiding-clear",
          onClick: Be
        }, m(p(c)("flowEditor.filter.clear")), 1)
      ])) : T("", !0),
      M.value ? (i(), u("div", Lr, [
        t("div", Vr, [
          t("span", {
            class: "palette-add-label",
            title: M.value.name
          }, m(M.value.name), 9, Ar),
          be.value ? (i(), u("span", {
            key: 0,
            class: "palette-add-source",
            title: be.value
          }, m(be.value), 9, Nr)) : T("", !0),
          !M.value.type && R(M.value.name).length ? (i(), u("div", Rr, [
            (i(!0), u(x, null, X(R(M.value.name), (P, G) => (i(), u("span", {
              key: G,
              class: "palette-arg-tag"
            }, m(P), 1))), 128))
          ])) : T("", !0),
          M.value.type ? T("", !0) : (i(), u("div", Fr, [
            ke.value ? (i(), u("span", Pr, m(p(c)("flowEditor.docModal.loading")), 1)) : Fe.value ? (i(), u("p", {
              key: 1,
              class: "palette-add-doc-text",
              "data-testid": "palette-add-doc-text",
              title: Fe.value
            }, m(Fe.value), 9, Dr)) : (i(), u("span", Mr, m(p(c)("flowEditor.docModal.noDoc")), 1))
          ]))
        ]),
        t("button", {
          class: "palette-add-btn",
          onClick: ie
        }, "+")
      ])) : T("", !0),
      t("div", xr, [
        bt.value.length ? (i(), u(x, { key: 0 }, [
          t("div", Ur, [
            w[4] || (w[4] = t("span", { class: "palette-section-glyph" }, "📄", -1)),
            t("span", null, m(p(c)("flowEditor.resourcesSectionLabel")), 1),
            t("span", {
              class: "palette-section-info",
              title: p(c)("flowEditor.resourcesSectionHint")
            }, "ⓘ", 8, Br)
          ]),
          (i(!0), u(x, null, X(bt.value, (P) => (i(), u("div", {
            key: P.name + (P.relPath || ""),
            class: ne(["palette-category palette-category--resource", { "palette-category--current": P.isCurrentFile }])
          }, [
            t("div", {
              class: "category-header",
              onClick: (G) => J(P.name)
            }, [
              t("span", Hr, m(ae(P.name) ? "▼" : "▶"), 1),
              t("span", Or, m(P.isResourceFile ? "📄" : "🤖"), 1),
              t("span", Ir, [
                t("span", {
                  class: "category-file-name",
                  title: (P.relPath ? P.relPath + "/" : "") + P.name
                }, m(P.name), 9, Wr),
                P.relPath ? (i(), u("span", jr, m(P.relPath) + "/", 1)) : T("", !0)
              ]),
              P.isCurrentFile ? (i(), u("span", {
                key: 0,
                class: "category-current-badge",
                title: p(c)("flowEditor.currentFileCategoryHint")
              }, m(p(c)("flowEditor.currentFileCategoryBadge")), 9, qr)) : T("", !0),
              t("span", zr, m(P.keywords.length), 1)
            ], 8, Kr),
            ae(P.name) ? (i(!0), u(x, { key: 0 }, X(P.keywords, (G) => (i(), u("div", {
              key: G,
              class: ne(["palette-item palette-item-keyword", { selected: He(G) }]),
              title: G,
              draggable: "true",
              onDragstart: (ge) => wt(ge, G, Y(P, G)),
              onClick: (ge) => Pe(G, void 0, Y(P, G)),
              onDblclick: (ge) => At(G, Y(P, G))
            }, [
              w[5] || (w[5] = t("span", { class: "palette-icon" }, "⚙", -1)),
              t("div", Yr, [
                t("span", Xr, m(G), 1),
                R(G).length ? (i(), u("span", Zr, "(" + m(R(G).length) + ")", 1)) : T("", !0)
              ])
            ], 42, Gr))), 128)) : T("", !0)
          ], 2))), 128)),
          w[6] || (w[6] = t("div", { class: "palette-section-divider" }, null, -1))
        ], 64)) : T("", !0),
        (i(!0), u(x, null, X(oo.value, (P) => (i(), u("div", {
          key: P.name,
          class: "palette-category"
        }, [
          t("div", {
            class: "category-header",
            onClick: (G) => J(P.name)
          }, [
            t("span", Jr, m(ae(P.name) ? "▼" : "▶"), 1),
            t("span", ei, m(P.name), 1),
            "isExamples" in P && P.isExamples ? (i(), u("span", {
              key: 0,
              class: "category-examples-badge",
              title: p(c)("flowEditor.examplesCategoryHint")
            }, m(p(c)("flowEditor.examplesCategoryBadge")), 9, ti)) : T("", !0),
            t("span", oi, m("keywords" in P ? P.keywords.length : P.items.length), 1)
          ], 8, Qr),
          ae(P.name) ? (i(), u(x, { key: 0 }, [
            "keywords" in P ? (i(!0), u(x, { key: 0 }, X(P.keywords, (G) => (i(), u("div", {
              key: G,
              class: ne([
                "palette-item",
                "palette-item-keyword",
                {
                  selected: He(G),
                  "palette-item--not-imported": !_(P)
                }
              ]),
              title: _(P) ? G : p(c)("flowEditor.keywordNotImportedHint", { library: P.name }),
              draggable: "true",
              onDragstart: (ge) => wt(ge, G, Y(P, G)),
              onClick: (ge) => Pe(G, void 0, Y(P, G)),
              onDblclick: (ge) => At(G, Y(P, G))
            }, [
              w[7] || (w[7] = t("span", { class: "palette-icon" }, "⚙", -1)),
              t("div", ai, [
                t("span", si, m(G), 1),
                R(G).length ? (i(), u("span", li, "(" + m(R(G).length) + ")", 1)) : T("", !0),
                _(P) ? T("", !0) : (i(), u("span", {
                  key: 1,
                  class: "palette-item-import-badge",
                  title: p(c)("flowEditor.keywordNotImportedBadgeTitle")
                }, "+ lib", 8, ri))
              ])
            ], 42, ni))), 128)) : T("", !0),
            "items" in P ? (i(!0), u(x, { key: 1 }, X(P.items, (G) => (i(), u("div", {
              key: G.label,
              class: ne(["palette-item", "palette-item-control", { selected: He(G.label) }]),
              title: G.label,
              draggable: "true",
              onDragstart: (ge) => _t(ge, G.type),
              onClick: (ge) => Pe(G.label, G.type),
              onDblclick: (ge) => Nt(G.type)
            }, [
              w[8] || (w[8] = t("span", { class: "palette-icon" }, "◆", -1)),
              t("span", ui, m(G.label), 1)
            ], 42, ii))), 128)) : T("", !0)
          ], 64)) : T("", !0)
        ]))), 128)),
        to.value.length ? T("", !0) : (i(), u("div", ci, m(p(c)("flowEditor.noResults") || "No matching keywords."), 1))
      ])
    ]));
  }
}), pi = /* @__PURE__ */ qe(di, [["__scopeId", "data-v-504d2430"]]), fi = { class: "kw-autocomplete" }, mi = ["value", "placeholder"], gi = ["aria-selected", "onMouseenter", "onMousedown"], vi = { class: "kw-autocomplete-name" }, hi = { class: "kw-autocomplete-lib" }, yi = /* @__PURE__ */ je({
  __name: "KeywordAutocompleteInput",
  props: {
    value: {},
    placeholder: {}
  },
  emits: ["update:value", "select"],
  setup(e, { emit: n }) {
    const r = e, c = n, { t: g } = mt(), { argsByName: a } = Tn(), _ = zo(), y = N(r.value);
    Le(() => r.value, (U) => {
      y.value = U;
    });
    const k = N(null), A = N(null), V = N(!1), E = N(-1), B = O(() => {
      const U = /* @__PURE__ */ new Map();
      for (const Z of _.keywords)
        Z.name && U.set(Z.name.toLowerCase(), { display: Z.name, library: Z.library || "" });
      return U;
    });
    function I(U) {
      return U.replace(/\b\w/g, (Z) => Z.toUpperCase());
    }
    const j = O(() => {
      const U = y.value.trim().toLowerCase();
      if (U.length < 2) return [];
      const Z = Array.from(a.value.keys()), ce = [], ke = [];
      for (const ee of Z) {
        if (ee === U) continue;
        const W = B.value.get(ee), oe = {
          name: (W == null ? void 0 : W.display) ?? I(ee),
          library: (W == null ? void 0 : W.library) ?? "BuiltIn"
        };
        ee.startsWith(U) ? ce.push(oe) : ee.includes(U) && ke.push(oe);
      }
      return ce.sort((ee, W) => ee.name.length - W.name.length || ee.name.localeCompare(W.name)), ke.sort((ee, W) => ee.name.localeCompare(W.name)), [...ce.slice(0, 15), ...ke.slice(0, 15)];
    });
    function ue(U) {
      const Z = U.target.value;
      y.value = Z, c("update:value", Z), V.value = !0, E.value = -1;
    }
    function M(U) {
      y.value = U, c("update:value", U), c("select", U), V.value = !1, E.value = -1;
    }
    function J(U) {
      const Z = j.value;
      if (U.key === "Enter") {
        U.preventDefault(), V.value && E.value >= 0 && E.value < Z.length ? M(Z[E.value].name) : (c("select", y.value), V.value = !1);
        return;
      }
      if (U.key === "Escape") {
        V.value = !1, E.value = -1;
        return;
      }
      if (U.key === "Tab") {
        V.value = !1, E.value = -1;
        return;
      }
      !V.value || Z.length === 0 || (U.key === "ArrowDown" ? (U.preventDefault(), E.value = Math.min(E.value + 1, Z.length - 1), ae()) : U.key === "ArrowUp" && (U.preventDefault(), E.value = Math.max(E.value - 1, 0), ae()));
    }
    function ae() {
      Xe(() => {
        var Z, ce;
        const U = (ce = (Z = A.value) == null ? void 0 : Z.children) == null ? void 0 : ce[E.value];
        U && typeof U.scrollIntoView == "function" && U.scrollIntoView({ block: "nearest" });
      });
    }
    function z() {
      j.value.length > 0 && (V.value = !0);
    }
    function D(U) {
      const Z = U.target;
      Z instanceof Element && (Z.closest(".kw-autocomplete") || (V.value = !1));
    }
    let R = !1;
    function H() {
      R || (document.addEventListener("click", D), R = !0);
    }
    function Y() {
      R && (document.removeEventListener("click", D), R = !1);
    }
    return Le(V, (U) => {
      U ? H() : Y();
    }), $n(Y), (U, Z) => (i(), u("div", fi, [
      t("input", {
        ref_key: "inputRef",
        ref: k,
        value: y.value,
        placeholder: e.placeholder ?? p(g)("flowEditor.keyword"),
        class: "flow-input",
        autocomplete: "off",
        "data-testid": "kw-autocomplete-input",
        onInput: ue,
        onFocus: z,
        onKeydown: J
      }, null, 40, mi),
      V.value && j.value.length > 0 ? (i(), u("ul", {
        key: 0,
        ref_key: "dropdownRef",
        ref: A,
        class: "kw-autocomplete-dropdown",
        role: "listbox",
        "data-testid": "kw-autocomplete-dropdown"
      }, [
        (i(!0), u(x, null, X(j.value, (ce, ke) => (i(), u("li", {
          key: `${ce.library}-${ce.name}-${ke}`,
          class: ne(["kw-autocomplete-item", { "is-highlighted": ke === E.value }]),
          role: "option",
          "aria-selected": ke === E.value,
          onMouseenter: (ee) => E.value = ke,
          onMousedown: (ee) => M(ce.name)
        }, [
          t("span", vi, m(ce.name), 1),
          t("span", hi, m(ce.library), 1)
        ], 42, gi))), 128))
      ], 512)) : T("", !0)
    ]));
  }
}), bi = /* @__PURE__ */ qe(yi, [["__scopeId", "data-v-358af914"]]), wi = {
  key: 0,
  class: "modal-header"
}, _i = { class: "modal-body" }, ki = {
  key: 1,
  class: "modal-footer"
}, Ci = /* @__PURE__ */ je({
  __name: "BaseModal",
  props: {
    modelValue: { type: Boolean },
    title: {},
    size: {}
  },
  emits: ["update:modelValue"],
  setup(e, { emit: n }) {
    const r = n;
    function c() {
      r("update:modelValue", !1);
    }
    function g(a) {
      a.key === "Escape" && c();
    }
    return qo(() => document.addEventListener("keydown", g)), Ho(() => document.removeEventListener("keydown", g)), (a, _) => (i(), Ut(yn, { to: "body" }, [
      ve(is, { name: "fade" }, {
        default: _e(() => [
          e.modelValue ? (i(), u("div", {
            key: 0,
            class: "modal-backdrop",
            onClick: re(c, ["self"])
          }, [
            t("div", {
              class: ne(["modal", [`modal-${e.size || "md"}`]])
            }, [
              e.title ? (i(), u("div", wi, [
                t("h3", null, m(e.title), 1),
                t("button", {
                  class: "modal-close",
                  onClick: c
                }, "×")
              ])) : T("", !0),
              t("div", _i, [
                Yn(a.$slots, "default", {}, void 0, !0)
              ]),
              a.$slots.footer ? (i(), u("div", ki, [
                Yn(a.$slots, "footer", {}, void 0, !0)
              ])) : T("", !0)
            ], 2)
          ])) : T("", !0)
        ]),
        _: 3
      })
    ]));
  }
}), Io = /* @__PURE__ */ qe(Ci, [["__scopeId", "data-v-c981bfa5"]]), $i = {
  key: 0,
  class: "kw-doc"
}, Ti = { class: "kw-doc-header" }, Ei = { class: "kw-doc-name" }, Si = { class: "kw-doc-lib" }, Li = {
  key: 0,
  class: "kw-doc-args"
}, Vi = { class: "kw-doc-body" }, Ai = ["innerHTML"], Ni = { key: 1 }, Ri = {
  key: 2,
  class: "kw-doc-empty"
}, Fi = {
  key: 3,
  class: "kw-doc-empty"
}, Pi = {
  key: 1,
  class: "kw-doc"
}, Di = { class: "kw-doc-empty" }, Mi = /* @__PURE__ */ je({
  __name: "KeywordDocModal",
  props: {
    modelValue: { type: Boolean },
    keyword: {},
    scopeId: {}
  },
  emits: ["update:modelValue"],
  setup(e) {
    const n = e, { t: r } = mt(), { getKeywordInfo: c, fetchKeywordInfo: g } = Tn(), a = N(null), _ = N(!1), y = O(() => a.value ?? c(n.keyword));
    async function k() {
      if (a.value = null, !n.modelValue || !n.keyword) return;
      const I = c(n.keyword);
      if (!(I != null && I.doc && I.doc.trim().length > 0)) {
        _.value = !0;
        try {
          const j = await g(n.keyword, n.scopeId);
          j && (a.value = j);
        } finally {
          _.value = !1;
        }
      }
    }
    Le(() => [n.modelValue, n.keyword], k, { immediate: !0 });
    const A = O(() => y.value ? y.value.args.map(ta) : []), V = O(() => {
      var I;
      return ((I = y.value) == null ? void 0 : I.docFormat) === "html";
    }), E = O(() => !y.value || !V.value ? "" : Va.sanitize(y.value.doc, {
      ALLOWED_TAGS: [
        "p",
        "br",
        "hr",
        "a",
        "code",
        "pre",
        "span",
        "strong",
        "b",
        "em",
        "i",
        "u",
        "ul",
        "ol",
        "li",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "table",
        "thead",
        "tbody",
        "tr",
        "th",
        "td",
        "blockquote"
      ],
      ALLOWED_ATTR: ["href", "title", "class"],
      ALLOW_DATA_ATTR: !1
    }));
    function B(I) {
      let j = I.name;
      return I.kind === "varargs" && (j = "*" + j), I.kind === "kwargs" && (j = "**" + j), I.type && (j += `: ${I.type}`), I.defaultValue !== null && (j += ` = ${I.defaultValue}`), j;
    }
    return (I, j) => (i(), Ut(Io, {
      "model-value": e.modelValue,
      title: p(r)("flowEditor.docModal.title"),
      size: "lg",
      "onUpdate:modelValue": j[0] || (j[0] = (ue) => I.$emit("update:modelValue", ue))
    }, {
      default: _e(() => [
        y.value ? (i(), u("div", $i, [
          t("div", Ti, [
            t("span", Ei, m(y.value.display), 1),
            t("span", Si, m(y.value.library), 1)
          ]),
          A.value.length > 0 ? (i(), u("div", Li, [
            t("h4", null, m(p(r)("flowEditor.docModal.argsHeader")), 1),
            t("ul", null, [
              (i(!0), u(x, null, X(A.value, (ue, M) => (i(), u("li", {
                key: M,
                class: "kw-doc-arg"
              }, [
                t("code", null, m(B(ue)), 1)
              ]))), 128))
            ])
          ])) : T("", !0),
          t("div", Vi, [
            V.value && y.value.doc.trim().length > 0 ? (i(), u("div", {
              key: 0,
              class: "kw-doc-html",
              innerHTML: E.value
            }, null, 8, Ai)) : y.value.doc.trim().length > 0 ? (i(), u("pre", Ni, m(y.value.doc), 1)) : _.value ? (i(), u("p", Ri, m(p(r)("flowEditor.docModal.loading")), 1)) : (i(), u("p", Fi, m(p(r)("flowEditor.docModal.noDoc")), 1))
          ])
        ])) : (i(), u("div", Pi, [
          t("p", Di, m(p(r)("flowEditor.docModal.noDoc")), 1)
        ]))
      ]),
      _: 1
    }, 8, ["model-value", "title"]));
  }
}), xi = /* @__PURE__ */ qe(Mi, [["__scopeId", "data-v-737ea011"]]), Ui = /* @__PURE__ */ new Set(["text", "css", "role", "aria"]);
function Bi(e) {
  return e.includes(">> nth=") || e.includes(":nth-match(") || e.includes(":nth-of-type(") || e.includes(">>>") || e.includes(">>");
}
function Oa(e) {
  let n;
  return e.strategy === "xpath" ? n = `xpath=${e.value}` : e.strategy === "text" ? n = e.value.startsWith("text=") ? e.value : `text=${e.value}` : n = e.value, !e.verified_unique && Ui.has(e.strategy) && !Bi(n) && !(e.strategy === "css" && e.value.includes("#")) && (n = `${n} >> nth=0`), n;
}
function Ia(e) {
  try {
    const r = new URL(e).host;
    return r ? `iframe[src*="${r}"]` : null;
  } catch {
    return null;
  }
}
function Ki(e) {
  if (!e.frame_chain || e.frame_chain.length === 0) return null;
  const n = [];
  for (const r of e.frame_chain)
    if (r.selector_candidates && r.selector_candidates.length > 0)
      n.push(r.selector_candidates[0].value);
    else {
      const c = r.url ? Ia(r.url) : null;
      if (c === null)
        return null;
      n.push(c);
    }
  return n.join(" >>> ");
}
function ko(e, n) {
  if (n.effective_override != null && n.effective_override.trim() !== "")
    return n.effective_override;
  const r = Oa(n), c = Ki(e);
  if (c !== null)
    return `${c} >>> ${r}`;
  if (e.frame_url) {
    const g = Ia(e.frame_url);
    if (g !== null)
      return `${g} >>> ${r}`;
  }
  return r;
}
const Hi = ["title"], Oi = ["title"], Ii = ["aria-expanded", "aria-label"], Wi = {
  key: 1,
  class: "selector-picker__menu",
  role: "listbox"
}, ji = ["aria-selected", "onClick"], qi = { class: "selector-picker__strategy" }, zi = ["title"], Gi = { class: "selector-picker__score" }, Yi = ["title"], Xi = ["title", "aria-label", "onClick"], Zi = ["title"], Qi = ["value"], Ji = ["placeholder", "onKeydown"], eu = ["title"], tu = ["title"], ou = { class: "selector-picker__effective-row" }, nu = { class: "selector-picker__effective-label" }, au = ["placeholder", "title", "onKeydown"], su = ["title"], lu = ["title"], ru = {
  class: "selector-picker__add-row",
  role: "presentation"
}, iu = ["title"], uu = ["value"], cu = ["placeholder", "onKeydown"], du = ["title"], pu = ["title"], fu = { class: "selector-picker__effective-row" }, mu = { class: "selector-picker__effective-label" }, gu = ["placeholder", "title", "onKeydown"], vu = ["title"], hu = ["title"], yu = /* @__PURE__ */ je({
  __name: "SelectorPicker",
  props: {
    command: {},
    compact: { type: Boolean }
  },
  emits: ["update:activeIndex", "update:candidate", "add:candidate"],
  setup(e, { emit: n }) {
    function r(pe, se, me) {
      return ko(pe, {
        strategy: me,
        value: se,
        verified_unique: !1
      });
    }
    const c = e, g = n, { t: a } = mt(), _ = N(!1), y = [
      "css",
      "xpath",
      "text",
      "aria",
      "testid",
      "automation_id",
      "uia_name",
      "uia_class_name"
    ], k = O(() => c.command.selector_candidates.length === 0 ? null : c.command.selector_candidates[c.command.active_candidate_index] ?? null), A = /* @__PURE__ */ new Set(["pw_locator"]), V = O(
      () => c.command.selector_candidates.map((pe, se) => ({ candidate: pe, originalIndex: se })).filter(({ candidate: pe }) => !A.has(pe.strategy))
    ), E = O(() => V.value.length > 1), B = O(() => V.value.length >= 1);
    function I(pe) {
      g("update:activeIndex", pe), _.value = !1;
    }
    function j(pe) {
      return ko(c.command, pe);
    }
    function ue(pe) {
      return `recorder.selector.strategy.${pe}`;
    }
    function M(pe) {
      const se = pe.target;
      se && se instanceof Element && se.closest(".selector-picker") || (_.value = !1, be());
    }
    function J() {
      document.addEventListener("click", M);
    }
    function ae() {
      document.removeEventListener("click", M);
    }
    function z() {
      _.value = !_.value, _.value ? J() : ae();
    }
    $n(ae);
    const D = N(null), R = N(""), H = N("css"), Y = N(""), U = N(!1), Z = N(null), ce = N(!1), ke = N(""), ee = N("css"), W = N(""), oe = N(!1), fe = N(null);
    function be() {
      D.value = null, ce.value = !1;
    }
    function Fe(pe) {
      const se = c.command.selector_candidates[pe];
      se && (ce.value = !1, D.value = pe, R.value = se.value, H.value = se.strategy, Y.value = se.effective_override != null && se.effective_override.trim() !== "" ? se.effective_override : ko(c.command, se), U.value = !1, Xe(() => {
        var me, Se;
        (Se = (me = Z.value) == null ? void 0 : me.focus) == null || Se.call(me);
      }));
    }
    function Pe() {
      if (D.value === null) return;
      const pe = R.value.trim();
      if (!pe) {
        D.value = null;
        return;
      }
      const se = Y.value.trim(), me = r(c.command, pe, H.value), Se = se === "" || se === me ? "" : se;
      g("update:candidate", {
        index: D.value,
        value: pe,
        strategy: H.value,
        effective: Se
      }), D.value = null;
    }
    function He() {
      D.value = null;
    }
    function ie() {
      D.value = null, ce.value = !0, ke.value = "", ee.value = "css", W.value = "", oe.value = !1, Xe(() => {
        var pe, se;
        (se = (pe = fe.value) == null ? void 0 : pe.focus) == null || se.call(pe);
      });
    }
    function Ie() {
      const pe = ke.value.trim();
      if (!pe) {
        ce.value = !1;
        return;
      }
      const se = W.value.trim(), me = r(c.command, pe, ee.value), Se = se === "" || se === me ? "" : se;
      g("add:candidate", {
        value: pe,
        strategy: ee.value,
        effective: Se
      }), ce.value = !1;
    }
    function tt() {
      ce.value = !1;
    }
    function gt(pe) {
      const se = pe.trim();
      return se.startsWith("//") || se.startsWith("..") || se.startsWith("xpath=") ? "xpath" : se.startsWith("text=") ? "text" : /^\[(data-test-id|data-testid|data-qa|data-test)=/i.test(se) ? "testid" : /^\[role=/.test(se) || se.startsWith("aria/") || /^role=/.test(se) ? "aria" : "css";
    }
    Le(ke, (pe) => {
      ee.value = gt(pe);
    }), Le([R, H], ([pe, se]) => {
      D.value !== null && (U.value || (Y.value = r(c.command, pe, se)));
    }), Le([ke, ee], ([pe, se]) => {
      ce.value && (oe.value || (W.value = r(c.command, pe, se)));
    });
    function ut() {
      U.value = !0;
    }
    function Ze() {
      oe.value = !0;
    }
    function vt() {
      Y.value = r(
        c.command,
        R.value.trim(),
        H.value
      ), U.value = !1;
    }
    function ze() {
      W.value = r(
        c.command,
        ke.value.trim(),
        ee.value
      ), oe.value = !1;
    }
    const Q = O(() => {
      if (D.value === null) return !1;
      const pe = R.value.trim();
      if (!pe) return !1;
      const se = r(c.command, pe, H.value), me = Y.value.trim();
      return me !== "" && me !== se;
    }), ot = O(() => {
      if (!ce.value) return !1;
      const pe = ke.value.trim();
      if (!pe) return !1;
      const se = r(c.command, pe, ee.value), me = W.value.trim();
      return me !== "" && me !== se;
    });
    return (pe, se) => k.value ? (i(), u("span", {
      key: 0,
      class: ne(["selector-picker", { "selector-picker--compact": e.compact }])
    }, [
      t("span", {
        class: ne(["selector-picker__dot", `selector-picker__dot--${p(vn)(k.value.quality_score)}`]),
        title: p(a)(ue(k.value.strategy)) + ` · ${k.value.quality_score}/100`,
        "aria-hidden": "true"
      }, null, 10, Hi),
      t("code", {
        class: "selector-picker__value",
        title: k.value.value
      }, m(j(k.value)), 9, Oi),
      B.value ? (i(), u("button", {
        key: 0,
        type: "button",
        class: "selector-picker__toggle",
        "aria-expanded": _.value,
        "aria-label": E.value ? p(a)("recorder.selector.swapAriaLabel") : p(a)("recorder.selector.editOrAddAriaLabel"),
        onClick: re(z, ["stop"])
      }, " ▾ ", 8, Ii)) : T("", !0),
      _.value ? (i(), u("ul", Wi, [
        (i(!0), u(x, null, X(V.value, ({ candidate: me, originalIndex: Se }) => (i(), u("li", {
          key: `${me.strategy}-${me.value}-${Se}`,
          role: "option",
          "aria-selected": Se === e.command.active_candidate_index,
          class: ne(["selector-picker__item", { "is-active": Se === e.command.active_candidate_index }]),
          onClick: (Be) => D.value !== Se && I(Se)
        }, [
          D.value !== Se ? (i(), u(x, { key: 0 }, [
            t("span", {
              class: ne(["selector-picker__dot", `selector-picker__dot--${p(vn)(me.quality_score)}`]),
              "aria-hidden": "true"
            }, null, 2),
            t("span", qi, m(p(a)(ue(me.strategy))), 1),
            t("code", {
              class: "selector-picker__value",
              title: me.value
            }, m(j(me)), 9, zi),
            t("span", Gi, m(me.quality_score), 1),
            me.verified_unique ? (i(), u("span", {
              key: 0,
              class: "selector-picker__unique",
              "aria-hidden": "true",
              title: p(a)("recorder.selector.verifiedUniqueTitle")
            }, "✓", 8, Yi)) : T("", !0),
            t("button", {
              type: "button",
              class: "selector-picker__edit",
              title: p(a)("recorder.selector.editTitle"),
              "aria-label": p(a)("recorder.selector.editTitle"),
              onClick: re((Be) => Fe(Se), ["stop"])
            }, "✏", 8, Xi)
          ], 64)) : (i(), u(x, { key: 1 }, [
            t("span", {
              class: ne(["selector-picker__dot", `selector-picker__dot--${p(vn)(me.quality_score)}`]),
              "aria-hidden": "true"
            }, null, 2),
            q(t("select", {
              "onUpdate:modelValue": se[0] || (se[0] = (Be) => H.value = Be),
              class: "selector-picker__strategy-select",
              title: p(a)("recorder.selector.strategyLabel")
            }, [
              (i(), u(x, null, X(y, (Be) => t("option", {
                key: Be,
                value: Be
              }, m(p(a)(ue(Be))), 9, Qi)), 64))
            ], 8, Zi), [
              [it, H.value]
            ]),
            q(t("input", {
              ref_for: !0,
              ref_key: "editInputRef",
              ref: Z,
              "onUpdate:modelValue": se[1] || (se[1] = (Be) => R.value = Be),
              class: "selector-picker__edit-input",
              placeholder: p(a)("recorder.selector.valuePlaceholder"),
              onKeydown: [
                Ye(re(Pe, ["prevent"]), ["enter"]),
                Ye(re(He, ["prevent"]), ["escape"])
              ]
            }, null, 40, Ji), [
              [le, R.value]
            ]),
            t("button", {
              type: "button",
              class: "selector-picker__edit-action selector-picker__edit-action--save",
              title: p(a)("editor.common.save"),
              onClick: re(Pe, ["stop"])
            }, "✓", 8, eu),
            t("button", {
              type: "button",
              class: "selector-picker__edit-action selector-picker__edit-action--cancel",
              title: p(a)("editor.common.cancel"),
              onClick: re(He, ["stop"])
            }, "×", 8, tu),
            t("div", ou, [
              t("label", nu, m(p(a)("recorder.selector.effectiveLabel")), 1),
              q(t("input", {
                "onUpdate:modelValue": se[2] || (se[2] = (Be) => Y.value = Be),
                class: ne(["selector-picker__effective-input", { "is-override": Q.value }]),
                placeholder: p(a)("recorder.selector.effectivePlaceholder"),
                title: p(a)("recorder.selector.effectiveTitle"),
                "data-testid": "selector-picker-effective-edit",
                onInput: ut,
                onKeydown: [
                  Ye(re(Pe, ["prevent"]), ["enter"]),
                  Ye(re(He, ["prevent"]), ["escape"])
                ]
              }, null, 42, au), [
                [le, Y.value]
              ]),
              Q.value ? (i(), u("button", {
                key: 0,
                type: "button",
                class: "selector-picker__effective-reset",
                title: p(a)("recorder.selector.effectiveResetTitle"),
                "data-testid": "selector-picker-effective-reset-edit",
                onClick: re(vt, ["stop"])
              }, "↺", 8, su)) : T("", !0),
              Q.value ? (i(), u("span", {
                key: 1,
                class: "selector-picker__effective-badge",
                title: p(a)("recorder.selector.effectiveOverrideTitle")
              }, m(p(a)("recorder.selector.effectiveOverrideBadge")), 9, lu)) : T("", !0)
            ])
          ], 64))
        ], 10, ji))), 128)),
        t("li", ru, [
          ce.value ? (i(), u(x, { key: 1 }, [
            se[6] || (se[6] = t("span", {
              class: "selector-picker__dot selector-picker__dot--neutral",
              "aria-hidden": "true"
            }, null, -1)),
            q(t("select", {
              "onUpdate:modelValue": se[3] || (se[3] = (me) => ee.value = me),
              class: "selector-picker__strategy-select",
              title: p(a)("recorder.selector.strategyLabel")
            }, [
              (i(), u(x, null, X(y, (me) => t("option", {
                key: me,
                value: me
              }, m(p(a)(ue(me))), 9, uu)), 64))
            ], 8, iu), [
              [it, ee.value]
            ]),
            q(t("input", {
              ref_key: "addInputRef",
              ref: fe,
              "onUpdate:modelValue": se[4] || (se[4] = (me) => ke.value = me),
              class: "selector-picker__edit-input",
              placeholder: p(a)("recorder.selector.valuePlaceholder"),
              onKeydown: [
                Ye(re(Ie, ["prevent"]), ["enter"]),
                Ye(re(tt, ["prevent"]), ["escape"])
              ]
            }, null, 40, cu), [
              [le, ke.value]
            ]),
            t("button", {
              type: "button",
              class: "selector-picker__edit-action selector-picker__edit-action--save",
              "data-testid": "selector-picker-add-save",
              title: p(a)("editor.common.save"),
              onClick: re(Ie, ["stop"])
            }, "✓", 8, du),
            t("button", {
              type: "button",
              class: "selector-picker__edit-action selector-picker__edit-action--cancel",
              title: p(a)("editor.common.cancel"),
              onClick: re(tt, ["stop"])
            }, "×", 8, pu),
            t("div", fu, [
              t("label", mu, m(p(a)("recorder.selector.effectiveLabel")), 1),
              q(t("input", {
                "onUpdate:modelValue": se[5] || (se[5] = (me) => W.value = me),
                class: ne(["selector-picker__effective-input", { "is-override": ot.value }]),
                placeholder: p(a)("recorder.selector.effectivePlaceholder"),
                title: p(a)("recorder.selector.effectiveTitle"),
                "data-testid": "selector-picker-effective-add",
                onInput: Ze,
                onKeydown: [
                  Ye(re(Ie, ["prevent"]), ["enter"]),
                  Ye(re(tt, ["prevent"]), ["escape"])
                ]
              }, null, 42, gu), [
                [le, W.value]
              ]),
              ot.value ? (i(), u("button", {
                key: 0,
                type: "button",
                class: "selector-picker__effective-reset",
                title: p(a)("recorder.selector.effectiveResetTitle"),
                "data-testid": "selector-picker-effective-reset-add",
                onClick: re(ze, ["stop"])
              }, "↺", 8, vu)) : T("", !0),
              ot.value ? (i(), u("span", {
                key: 1,
                class: "selector-picker__effective-badge",
                title: p(a)("recorder.selector.effectiveOverrideTitle")
              }, m(p(a)("recorder.selector.effectiveOverrideBadge")), 9, hu)) : T("", !0)
            ])
          ], 64)) : (i(), u("button", {
            key: 0,
            type: "button",
            class: "selector-picker__add-btn",
            "data-testid": "selector-picker-add",
            onClick: re(ie, ["stop"])
          }, "+ " + m(p(a)("recorder.selector.addCustom")), 1))
        ])
      ])) : T("", !0)
    ], 2)) : T("", !0);
  }
}), bu = /* @__PURE__ */ qe(yu, [["__scopeId", "data-v-00ec4dc3"]]);
async function Wa(e) {
  return ye().startDebugSession(e);
}
async function wu(e) {
  return Wa(e);
}
async function ga(e, n) {
  return ye().postDebugControl(e, n);
}
async function _u(e) {
  return ye().getDebugState(e);
}
async function ku(e) {
  return ye().installDebugPrerequisites(e);
}
function jn(e) {
  if (e && typeof e == "object" && "response" in e) {
    const n = e.response;
    if (n && typeof n == "object" && "status" in n) {
      const r = n.status;
      if (typeof r == "number") return r;
    }
  }
  return null;
}
function Co(e, n) {
  if (e && typeof e == "object" && "response" in e) {
    const r = e.response;
    if (r && typeof r == "object" && "data" in r) {
      const c = r.data;
      if (c && typeof c == "object" && "detail" in c) {
        const g = c.detail;
        if (typeof g == "string" && g.length > 0)
          return g;
        if (g && typeof g == "object" && "message" in g && typeof g.message == "string")
          return g.message;
      }
      if (typeof c == "string" && c.length > 0)
        return c;
    }
  }
  return n;
}
const Cu = { file: null, line: null, keyword: null }, qn = {
  session_id: "",
  paused: !1,
  terminated: !1,
  paused_at: Cu,
  scopes: [],
  call_stack: [],
  output_lines: []
}, ja = $o("debug", () => {
  const e = N(null), n = N(null), r = N(null), c = N(null), g = N({ ...qn }), a = N([]), _ = N(!1), y = N(null), k = N(null), A = N(!1), V = N(null);
  let E = null;
  const B = O(() => e.value !== null && !g.value.terminated), I = O(() => g.value.paused), j = O(() => g.value.paused_at), ue = O(() => g.value.scopes), M = O(() => g.value.call_stack);
  async function J(W) {
    _.value = !0, y.value = null;
    try {
      try {
        const oe = await Wa({ run_id: W });
        return D(oe), oe;
      } catch (oe) {
        if (jn(oe) === 424)
          return Z(oe, { kind: "run", runId: W }), null;
        throw oe;
      }
    } finally {
      _.value = !1;
    }
  }
  async function ae(W) {
    var oe, fe;
    _.value = !0, y.value = null;
    try {
      try {
        const be = await wu(W);
        return D(be), { resp: be, resumed: !1 };
      } catch (be) {
        if (jn(be) === 424)
          return Z(be, { kind: "step", payload: W }), null;
        if (jn(be) === 409) {
          const Fe = (fe = (oe = be == null ? void 0 : be.response) == null ? void 0 : oe.data) == null ? void 0 : fe.detail;
          if (Fe && typeof Fe == "object") {
            const Pe = Fe, He = {
              session_id: String(Pe.session_id ?? ""),
              robot_file: String(Pe.robot_file ?? W.file),
              breakpoint_line: Number(Pe.breakpoint_line ?? W.line),
              test_name: Pe.test_name ?? null
            };
            if (He.session_id)
              return D(He), { resp: He, resumed: !0 };
          }
        }
        throw be;
      }
    } finally {
      _.value = !1;
    }
  }
  function z(W, oe) {
    return !e.value || g.value.terminated ? "idle" : n.value === W && r.value === oe ? "same" : "different";
  }
  function D(W) {
    e.value = W.session_id, n.value = W.robot_file, r.value = W.breakpoint_line, c.value = W.test_name, g.value = { ...qn, session_id: W.session_id }, a.value = [];
  }
  async function R() {
    e.value && (g.value = await _u(e.value));
  }
  async function H(W) {
    e.value && await ga(e.value, W);
  }
  async function Y() {
    if (!e.value) return;
    const W = e.value;
    try {
      await ga(W, "disconnect");
    } finally {
      U();
    }
  }
  function U() {
    e.value = null, n.value = null, r.value = null, c.value = null, g.value = { ...qn }, a.value = [], y.value = null, k.value = null, A.value = !1, V.value = null, E = null;
  }
  function Z(W, oe) {
    var be, Fe;
    E = oe;
    const fe = (Fe = (be = W == null ? void 0 : W.response) == null ? void 0 : be.data) == null ? void 0 : Fe.detail;
    if (fe && typeof fe == "object") {
      const Pe = fe;
      k.value = {
        scopeId: Number(Pe.scope_id ?? 0),
        envId: Number(Pe.env_id ?? 0),
        packageName: String(Pe.package ?? "robotcode"),
        message: String(Pe.message ?? "")
      };
    } else
      k.value = {
        scopeId: 0,
        envId: 0,
        packageName: "robotcode",
        message: ""
      };
    V.value = null;
  }
  async function ce() {
    if (!k.value || !E) return !1;
    A.value = !0, V.value = null;
    try {
      await ku(k.value.scopeId);
    } catch (oe) {
      return V.value = Co(oe, "Install failed"), A.value = !1, !1;
    }
    const W = E;
    k.value = null, E = null, A.value = !1;
    try {
      return W.kind === "run" ? await J(W.runId) !== null : await ae(W.payload) !== null;
    } catch (oe) {
      return y.value = Co(oe, "Retry failed"), !1;
    }
  }
  function ke() {
    k.value = null, E = null, A.value = !1, V.value = null;
  }
  function ee(W) {
    if (e.value && W.topic === `debug:session:${e.value}`)
      switch (W.kind) {
        case "state":
          g.value = W.body;
          break;
        case "output": {
          const oe = String(W.body.output ?? "").replace(/\n+$/, "");
          oe && a.value.push(oe), a.value.length > 300 && (a.value = a.value.slice(-300));
          break;
        }
        case "terminated":
          g.value = { ...g.value, terminated: !0, paused: !1 };
          break;
      }
  }
  return {
    sessionId: e,
    robotFile: n,
    breakpointLine: r,
    testName: c,
    state: g,
    outputLog: a,
    loading: _,
    lastError: y,
    isActive: B,
    paused: I,
    pausedAt: j,
    scopes: ue,
    callStack: M,
    prereqMissing: k,
    installing: A,
    installError: V,
    startFromRun: J,
    startFromStep: ae,
    classifyStepClick: z,
    refreshState: R,
    control: H,
    stop: Y,
    reset: U,
    handleWsEvent: ee,
    installPrereqAndRetry: ce,
    cancelPrereq: ke
  };
}), $u = {
  class: "debug-panel",
  "data-testid": "debug-panel"
}, Tu = { class: "debug-panel__header" }, Eu = { class: "debug-panel__title" }, Su = { class: "debug-panel__paused-at" }, Lu = {
  key: 0,
  class: "debug-panel__badge debug-panel__badge--terminated"
}, Vu = {
  key: 1,
  class: "debug-panel__badge debug-panel__badge--paused"
}, Au = { class: "debug-panel__toolbar" }, Nu = {
  key: 0,
  class: "debug-panel__error"
}, Ru = {
  key: 1,
  class: "debug-panel__line-callout",
  "data-testid": "debug-line-callout"
}, Fu = {
  class: "debug-panel__line-number",
  "data-testid": "debug-line-number"
}, Pu = { class: "debug-panel__line-label" }, Du = { class: "debug-panel__line-value" }, Mu = { class: "debug-panel__line-meta" }, xu = ["title"], Uu = {
  key: 1,
  class: "debug-panel__line-keyword"
}, Bu = { class: "debug-panel__body" }, Ku = { class: "debug-panel__stack" }, Hu = {
  key: 0,
  class: "debug-panel__stack-list"
}, Ou = ["title"], Iu = ["title"], Wu = { key: 0 }, ju = {
  key: 1,
  class: "debug-panel__empty"
}, qu = { class: "debug-panel__scopes" }, zu = {
  key: 0,
  class: "debug-panel__empty"
}, Gu = ["aria-expanded", "onClick"], Yu = { class: "debug-panel__scope-chevron" }, Xu = { class: "debug-panel__scope-name" }, Zu = { class: "debug-panel__scope-count" }, Qu = {
  key: 0,
  class: "debug-panel__scope-vars"
}, Ju = {
  key: 0,
  class: "debug-panel__empty"
}, ec = { class: "debug-panel__var-name" }, tc = {
  key: 0,
  class: "debug-panel__var-type"
}, oc = { class: "debug-panel__var-value" }, nc = { class: "debug-panel__output" }, ac = {
  key: 0,
  class: "debug-panel__empty"
}, sc = {
  key: 1,
  class: "debug-panel__output-log"
}, lc = /* @__PURE__ */ je({
  __name: "DebugPanel",
  props: {
    runId: {}
  },
  emits: ["closed"],
  setup(e, { emit: n }) {
    const r = e, c = n, { t: g } = mt(), a = ja(), _ = N(null);
    qo(async () => {
      if (r.runId !== void 0 && !a.isActive)
        try {
          await a.startFromRun(r.runId);
        } catch (z) {
          _.value = Co(z, g("debug.error.startFailed"));
        }
    }), Ho(() => {
    });
    const y = O(() => {
      const z = a.pausedAt;
      if (!z.file && !z.keyword) return g("debug.panel.notPaused");
      const D = z.line !== null ? `${z.file ?? ""}:${z.line}` : z.file ?? "";
      return z.keyword ? `${z.keyword}  —  ${D}` : D;
    });
    function k(z) {
      if (!z) return "";
      const D = Math.max(z.lastIndexOf("/"), z.lastIndexOf("\\"));
      return D >= 0 ? z.slice(D + 1) : z;
    }
    const A = O(() => k(a.pausedAt.file));
    async function V() {
      await ue(() => a.control("continue"));
    }
    async function E() {
      await ue(() => a.control("next"));
    }
    async function B() {
      await ue(() => a.control("stepIn"));
    }
    async function I() {
      await ue(() => a.control("stepOut"));
    }
    async function j() {
      try {
        await a.stop();
      } catch {
      } finally {
        c("closed");
      }
    }
    async function ue(z) {
      _.value = null;
      try {
        await z();
      } catch (D) {
        _.value = Co(D, g("debug.error.commandFailed"));
      }
    }
    const M = N(/* @__PURE__ */ new Set(["Local"]));
    function J(z) {
      M.value.has(z) ? M.value.delete(z) : M.value.add(z), M.value = new Set(M.value);
    }
    function ae(z, D = 200) {
      return z.length <= D ? z : z.slice(0, D) + "…";
    }
    return (z, D) => (i(), u("div", $u, [
      t("header", Tu, [
        t("div", Eu, [
          D[0] || (D[0] = t("span", {
            class: "debug-panel__icon",
            "aria-hidden": "true"
          }, "🐞", -1)),
          t("span", Su, m(y.value), 1),
          p(a).state.terminated ? (i(), u("span", Lu, m(p(g)("debug.panel.terminated")), 1)) : p(a).paused ? (i(), u("span", Vu, m(p(g)("debug.panel.paused")), 1)) : T("", !0)
        ]),
        t("div", Au, [
          ve(Oe, {
            size: "sm",
            variant: "primary",
            disabled: !p(a).paused || p(a).state.terminated,
            onClick: V
          }, {
            default: _e(() => [
              we("▶ " + m(p(g)("debug.panel.toolbar.continue")), 1)
            ]),
            _: 1
          }, 8, ["disabled"]),
          ve(Oe, {
            size: "sm",
            variant: "ghost",
            disabled: !p(a).paused || p(a).state.terminated,
            onClick: E
          }, {
            default: _e(() => [
              we("⤼ " + m(p(g)("debug.panel.toolbar.stepOver")), 1)
            ]),
            _: 1
          }, 8, ["disabled"]),
          ve(Oe, {
            size: "sm",
            variant: "ghost",
            disabled: !p(a).paused || p(a).state.terminated,
            onClick: B
          }, {
            default: _e(() => [
              we("↳ " + m(p(g)("debug.panel.toolbar.stepIn")), 1)
            ]),
            _: 1
          }, 8, ["disabled"]),
          ve(Oe, {
            size: "sm",
            variant: "ghost",
            disabled: !p(a).paused || p(a).state.terminated,
            onClick: I
          }, {
            default: _e(() => [
              we("↰ " + m(p(g)("debug.panel.toolbar.stepOut")), 1)
            ]),
            _: 1
          }, 8, ["disabled"]),
          ve(Oe, {
            size: "sm",
            variant: "danger",
            onClick: j
          }, {
            default: _e(() => [
              we(" ✕ " + m(p(g)("debug.panel.toolbar.stop")), 1)
            ]),
            _: 1
          })
        ])
      ]),
      _.value ? (i(), u("div", Nu, m(_.value), 1)) : T("", !0),
      p(a).paused && p(a).pausedAt.line !== null ? (i(), u("div", Ru, [
        t("div", Fu, [
          t("span", Pu, m(p(g)("debug.panel.lineLabel")), 1),
          t("span", Du, m(p(a).pausedAt.line), 1)
        ]),
        t("div", Mu, [
          A.value ? (i(), u("span", {
            key: 0,
            class: "debug-panel__line-file",
            title: p(a).pausedAt.file ?? ""
          }, m(A.value), 9, xu)) : T("", !0),
          p(a).pausedAt.keyword ? (i(), u("span", Uu, m(p(a).pausedAt.keyword), 1)) : T("", !0)
        ])
      ])) : T("", !0),
      t("div", Bu, [
        t("aside", Ku, [
          t("h3", null, m(p(g)("debug.panel.callStack")), 1),
          p(a).callStack.length ? (i(), u("ul", Hu, [
            (i(!0), u(x, null, X(p(a).callStack, (R, H) => (i(), u("li", {
              key: H,
              class: ne({ "debug-panel__stack-item": !0, "is-top": H === 0 })
            }, [
              t("span", {
                class: "debug-panel__stack-name",
                title: R.name
              }, m(R.name), 9, Ou),
              R.file ? (i(), u("span", {
                key: 0,
                class: "debug-panel__stack-file",
                title: R.file + (R.line ? ":" + R.line : "")
              }, [
                we(m(k(R.file)), 1),
                R.line ? (i(), u("span", Wu, ":" + m(R.line), 1)) : T("", !0)
              ], 8, Iu)) : T("", !0)
            ], 2))), 128))
          ])) : (i(), u("p", ju, m(p(g)("debug.panel.callStackEmpty")), 1))
        ]),
        t("section", qu, [
          t("h3", null, m(p(g)("debug.panel.scopes")), 1),
          p(a).scopes.length ? T("", !0) : (i(), u("div", zu, m(p(g)("debug.panel.scopesEmpty")), 1)),
          (i(!0), u(x, null, X(p(a).scopes, (R) => (i(), u("div", {
            key: R.name,
            class: "debug-panel__scope"
          }, [
            t("button", {
              class: "debug-panel__scope-header",
              "aria-expanded": M.value.has(R.name),
              onClick: (H) => J(R.name)
            }, [
              t("span", Yu, m(M.value.has(R.name) ? "▾" : "▸"), 1),
              t("span", Xu, m(R.name), 1),
              t("span", Zu, m(R.variables.length), 1)
            ], 8, Gu),
            M.value.has(R.name) ? (i(), u("div", Qu, [
              R.variables.length ? T("", !0) : (i(), u("div", Ju, m(p(g)("debug.panel.scopeEmpty")), 1)),
              (i(!0), u(x, null, X(R.variables, (H) => (i(), u("div", {
                key: H.name,
                class: "debug-panel__var"
              }, [
                t("span", ec, m(H.name), 1),
                H.type ? (i(), u("span", tc, "(" + m(H.type) + ")", 1)) : T("", !0),
                t("span", oc, m(ae(H.value)), 1)
              ]))), 128))
            ])) : T("", !0)
          ]))), 128))
        ])
      ]),
      t("footer", nc, [
        t("h3", null, m(p(g)("debug.panel.output")), 1),
        p(a).outputLog.length === 0 ? (i(), u("div", ac, m(p(g)("debug.panel.outputEmpty")), 1)) : (i(), u("pre", sc, m(p(a).outputLog.join(`
`)), 1))
      ])
    ]));
  }
}), rc = /* @__PURE__ */ qe(lc, [["__scopeId", "data-v-77a3a274"]]), ic = { class: "prereq-body" }, uc = {
  key: 0,
  class: "prereq-error",
  role: "alert"
}, cc = {
  key: 1,
  class: "prereq-status"
}, dc = /* @__PURE__ */ je({
  __name: "DebugPrereqDialog",
  props: {
    open: { type: Boolean },
    packageName: {},
    installing: { type: Boolean },
    installError: {}
  },
  emits: ["install", "cancel"],
  setup(e, { emit: n }) {
    const r = e, c = n, { t: g } = mt();
    function a() {
      c("install");
    }
    function _() {
      c("cancel");
    }
    return (y, k) => (i(), u(x, null, [
      r.open ? (i(), Ut(yn, {
        key: 0,
        to: "body"
      }, [
        k[0] || (k[0] = t("span", {
          "data-testid": "debug-prereq-dialog",
          style: { display: "none" },
          "aria-hidden": "true"
        }, null, -1))
      ])) : T("", !0),
      ve(Io, {
        "model-value": r.open,
        title: p(g)("debug.prereq.title"),
        size: "md"
      }, {
        footer: _e(() => [
          ve(Oe, {
            variant: "ghost",
            disabled: r.installing,
            "data-testid": "debug-prereq-cancel-btn",
            onClick: _
          }, {
            default: _e(() => [
              we(m(p(g)("debug.prereq.cancel")), 1)
            ]),
            _: 1
          }, 8, ["disabled"]),
          ve(Oe, {
            variant: "primary",
            loading: r.installing,
            disabled: r.installing,
            "data-testid": "debug-prereq-install-btn",
            onClick: a
          }, {
            default: _e(() => [
              we(m(r.installing ? p(g)("debug.prereq.installing", { package: r.packageName }) : p(g)("debug.prereq.install")), 1)
            ]),
            _: 1
          }, 8, ["loading", "disabled"])
        ]),
        default: _e(() => [
          t("p", ic, m(p(g)("debug.prereq.body", { package: r.packageName })), 1),
          r.installError ? (i(), u("p", uc, m(r.installError), 1)) : r.installing ? (i(), u("p", cc, m(p(g)("debug.prereq.installing", { package: r.packageName })), 1)) : T("", !0)
        ]),
        _: 1
      }, 8, ["model-value", "title"])
    ], 64));
  }
}), pc = /* @__PURE__ */ qe(dc, [["__scopeId", "data-v-82fc15e8"]]), fc = (e) => ye().login(e.email, e.password), mc = () => ye().getMe(), gc = (e) => ye().patchFirstLoginComplete(e);
async function vc() {
  const e = await ye().getFeatures();
  return { flags: e.flags ?? {}, locked: e.locked ?? {} };
}
const Xn = N({}), Zn = N({}), hn = N(!1);
let mn = null;
async function va() {
  try {
    const e = await vc();
    Xn.value = e.flags, Zn.value = e.locked, hn.value = !0;
  } catch {
  }
}
function ha() {
  !hn.value && !mn && (mn = va().finally(() => {
    mn = null;
  }));
  function e(c) {
    return Xn.value[c] !== !1;
  }
  function n(c) {
    return Zn.value[c] === !0;
  }
  function r() {
    hn.value = !1, Xn.value = {}, Zn.value = {}, mn = null;
  }
  return { isEnabled: e, isLocked: n, refresh: va, reset: r, loaded: hn };
}
const hc = $o("auth", () => {
  const e = N(null), n = N(localStorage.getItem("access_token")), r = O(() => !!n.value), c = O(() => e.value), g = O(() => {
    var V;
    return ((V = e.value) == null ? void 0 : V.role) ?? "viewer";
  });
  function a(V) {
    const E = { viewer: 0, runner: 1, editor: 2, admin: 3 };
    return (E[g.value] ?? -1) >= (E[V] ?? 999);
  }
  async function _(V, E) {
    const B = await fc({ email: V, password: E });
    n.value = B.access_token, localStorage.setItem("access_token", B.access_token), localStorage.setItem("refresh_token", B.refresh_token), await y(), ha().refresh();
  }
  async function y() {
    if (n.value)
      try {
        e.value = await mc();
      } catch {
        k();
      }
  }
  function k() {
    e.value = null, n.value = null, localStorage.removeItem("access_token"), localStorage.removeItem("refresh_token"), ha().reset();
  }
  async function A() {
    const V = await gc(!0);
    e.value = V;
  }
  return {
    user: e,
    token: n,
    isAuthenticated: r,
    currentUser: c,
    userRole: g,
    hasMinRole: a,
    login: _,
    logout: k,
    fetchCurrentUser: y,
    markFirstLoginComplete: A
  };
}), zn = 768, yc = $o("ui", () => {
  const e = N(window.innerWidth >= zn), n = N(window.innerWidth), r = N([]);
  let c = 0;
  const g = O(() => n.value < zn), a = N(localStorage.getItem("notifications_enabled") === "true"), _ = N(
    typeof Notification < "u" ? Notification.permission : "denied"
  );
  async function y() {
    if (typeof Notification > "u") return !1;
    const z = await Notification.requestPermission();
    return _.value = z, z === "granted";
  }
  async function k() {
    if (a.value) {
      a.value = !1, localStorage.setItem("notifications_enabled", "false");
      return;
    }
    _.value !== "granted" && !await y() || (a.value = !0, localStorage.setItem("notifications_enabled", "true"));
  }
  function A(z, D, R) {
    a.value && (typeof Notification > "u" || Notification.permission !== "granted" || document.hasFocus() || new Notification(z, { body: D, tag: R, icon: "/favicon.ico" }));
  }
  function V() {
    n.value = window.innerWidth, n.value < zn && (e.value = !1);
  }
  window.addEventListener("resize", V);
  function E() {
    e.value = !e.value;
  }
  function B() {
    g.value && (e.value = !1);
  }
  function I(z, D, R = "info", H = 5e3) {
    const Y = ++c;
    r.value.push({ id: Y, title: z, message: D, type: R, timeout: H }), H > 0 && setTimeout(() => j(Y), H);
  }
  function j(z) {
    r.value = r.value.filter((D) => D.id !== z);
  }
  function ue(z, D = "") {
    I(z, D, "success");
  }
  function M(z, D = "") {
    I(z, D, "error", 8e3);
  }
  function J(z, D = "") {
    I(z, D, "info");
  }
  function ae(z, D = "") {
    I(z, D, "warning");
  }
  return { sidebarOpen: e, isMobile: g, windowWidth: n, toasts: r, notificationsEnabled: a, notificationPermission: _, toggleSidebar: E, closeSidebarOnMobile: B, addToast: I, removeToast: j, success: ue, error: M, info: J, warning: ae, toggleNotifications: k, sendBrowserNotification: A };
});
function Qn() {
  const e = yc();
  return {
    success: e.success,
    error: e.error,
    info: e.info,
    warning: e.warning
  };
}
const bc = /* @__PURE__ */ new Set([
  "keyword",
  "assignment",
  "var",
  "for",
  "end",
  "if",
  "else_if",
  "else",
  "while",
  "try",
  "except",
  "finally",
  "break",
  "continue",
  "return",
  "comment"
]);
function wc(e) {
  return bc.has(e);
}
const _c = /* @__PURE__ */ new Set(["keyword", "assignment"]);
function kn(e) {
  return _c.has(e.type);
}
function kc(e, n) {
  if (n < 0 || n >= e.length || !kn(e[n])) return -1;
  let r = 0;
  for (let c = 0; c < n; c++)
    kn(e[c]) && r++;
  return r;
}
function Cc(e, n, r) {
  if (!n || r < 0 || r >= e.length) return null;
  const c = e[r];
  if (!kn(c)) return null;
  if (c.rbs_id)
    return n.commands.find((_) => _.id === c.rbs_id) ?? null;
  if (e.some((_) => kn(_) && !!_.rbs_id)) return null;
  const a = kc(e, r);
  return a < 0 ? null : n.commands[a] ?? null;
}
function $c(e, n) {
  if (!n || !e.keyword) return null;
  const r = n.get(e.keyword.toLowerCase());
  return r ? r.map(ta).filter(
    (c) => c.kind !== "positional-only-sep" && c.kind !== "named-only-sep" && c.kind !== "optional-sep"
  ) : null;
}
function Tc(e, n, r) {
  const c = n.selector_candidates[r];
  if (!c) return !1;
  const g = ko(n, c);
  return e.args.length === 0 ? e.args.push(g) : e.args[0] = g, n.active_candidate_index = r, !0;
}
function ya(e, n) {
  return ko(e, n);
}
const Ec = /\s*>>\s*nth=\d+\s*$/;
function Sc(e) {
  let n = e;
  const r = n.lastIndexOf(" >>> ");
  return r >= 0 && (n = n.slice(r + 5)), n.replace(Ec, "").trim();
}
function ba(e, n) {
  if (n.selector_candidates.length === 0) return !1;
  const r = e.args[0] ?? "";
  if (r === "" || n.selector_candidates.some((g) => g.value === r) || n.selector_candidates.some(
    (g) => ko(n, g) === r
  )) return !1;
  const c = Sc(r);
  return c === r ? !0 : !n.selector_candidates.some(
    (g) => g.value === c || Oa(g) === c
  );
}
const Jn = 50, Lc = 60, ro = 0, wa = 30, _a = 12, Vc = 16;
function Ac(e) {
  return {
    ...e,
    args: [...e.args],
    returnVars: [...e.returnVars],
    loopValues: [...e.loopValues],
    // _lineNumber is a primitive — covered by `...step`. Listed here
    // explicitly so a future contributor sees that DEBUG-3 metadata
    // intentionally rides through the clone.
    _lineNumber: e._lineNumber
  };
}
const na = 32, Nc = 44;
function Ko(e) {
  const n = Xa(e.type);
  if (n === "start" || n === "end") return na;
  let r = Nc;
  return (n === "keyword" || n === "assignment") && (e.args.length > 0 && (r += 4 + e.args.length * 22), e.returnVars.length > 0 && (r += 20)), n === "control" && (e.condition && (r += 20), (e.type === "for" || e.type === "while") && e.loopVar && e.loopValues.length > 0 && (r += 20), e.exceptPattern && (r += 20)), r;
}
const qa = /* @__PURE__ */ new Set(["if", "for", "while", "try"]);
function Rc(e, n) {
  let r = 1, c = n + 1;
  for (; c < e.length && r > 0; )
    qa.has(e[c].type) && r++, e[c].type === "end" && r--, c++;
  return c - 1;
}
const Fc = {
  if: { border: "#E8A838", bg: "rgba(232, 168, 56, 0.06)" },
  for: { border: "#7B61FF", bg: "rgba(123, 97, 255, 0.06)" },
  while: { border: "#7B61FF", bg: "rgba(123, 97, 255, 0.06)" },
  try: { border: "#38B2AC", bg: "rgba(56, 178, 172, 0.06)" }
};
function Pc(e, n, r) {
  za(e, n, r, 0, e.length, 0);
}
function za(e, n, r, c, g, a) {
  let _ = c;
  for (; _ < g; )
    if (qa.has(e[_].type)) {
      const y = Rc(e, _), k = [];
      for (let A = _; A < y; A++) {
        if (e[A].type === "end") continue;
        const V = n.find((E) => E.id === `${r}-step-${A}`);
        V && k.push(V);
      }
      if (k.length > 0) {
        const A = Math.min(...k.map((ue) => ue.position.y)), V = Math.max(...k.map((ue) => ue.position.y)), E = k[k.length - 1].data, B = Ko(E.step), I = Fc[e[_].type] || { border: "#888", bg: "rgba(0,0,0,0.03)" }, j = a * 14;
        n.push({
          id: `${r}-frame-${_}`,
          type: "control-frame",
          position: { x: ro - wa + j, y: A - _a },
          data: { stepType: e[_].type, borderColor: I.border, bgColor: I.bg },
          style: {
            width: `${320 + (wa - j) * 2}px`,
            height: `${V - A + B + _a + Vc}px`
          },
          zIndex: -1 - a,
          selectable: !1,
          draggable: !1
        });
      }
      za(e, n, r, _ + 1, y, a + 1), _ = y + 1;
    } else
      _++;
}
function Ga(e, n, r, c, g, a = null, _ = null) {
  const y = [], k = [];
  y.push({
    id: `${r}-start`,
    type: "start",
    position: { x: ro, y: 0 },
    data: { label: n || void 0 }
  });
  let A = `${r}-start`, V = na + Jn;
  for (let B = 0; B < e.length; B++) {
    const I = e[B];
    if (I.type === "end") continue;
    const j = `${r}-step-${B}`, ue = Xa(I.type), M = Wc(I), J = Ko(I);
    y.push({
      id: j,
      type: ue,
      position: { x: ro, y: V },
      data: {
        label: M,
        stepType: I.type,
        // Deep-clone the step's arrays so v-model edits inside the
        // detail panel don't mutate the form's arrays directly. The
        // shallow spread `{ ...step }` previously left `args` /
        // `returnVars` / `loopValues` as SHARED REFERENCES, so each
        // keystroke fired the deep watcher on `props.form` — which
        // reset `selectedNode` and tore down the panel mid-edit.
        // `updateStepFromNode` replaces the form's arrays on blur via
        // `Object.assign`, so the round-trip still lands.
        step: Ac(I),
        section: c,
        sectionIndex: g,
        stepIndex: B,
        recording: Cc(e, a, B),
        argSpecs: $c(I, _),
        bdd: I.type === "keyword" || I.type === "assignment" ? Aa(I.keyword) : null,
        envRefs: Fa([
          ...I.args,
          I.condition,
          ...I.loopValues
        ])
      }
    });
    const ae = jc(I, B > 0 ? e[B - 1] : void 0);
    k.push({
      id: `${r}-e-${A}-${j}`,
      source: A,
      target: j,
      animated: I.type === "if" || I.type === "for" || I.type === "try",
      label: ae
    }), A = j, V += J + Jn;
  }
  const E = `${r}-end`;
  return y.push({
    id: E,
    type: "end",
    position: { x: ro, y: V },
    data: {}
  }), k.push({
    id: `${r}-e-${A}-${E}`,
    source: A,
    target: E
  }), Pc(e, y, r), { nodes: y, edges: k };
}
const Dc = [
  "documentation",
  "tags",
  "setup",
  "teardown",
  "template",
  "timeout"
], Mc = [
  "documentation",
  "arguments",
  "tags",
  "setup",
  "teardown",
  "timeout"
], xc = {
  documentation: "[Documentation]",
  tags: "[Tags]",
  arguments: "[Arguments]",
  setup: "[Setup]",
  teardown: "[Teardown]",
  template: "[Template]",
  timeout: "[Timeout]"
};
function Uc(e, n) {
  switch (n) {
    case "documentation":
      return e.documentation;
    case "tags":
      return e.tags.join(", ");
    case "arguments":
      return "arguments" in e ? e.arguments.join(", ") : "";
    case "setup":
      return e.setup;
    case "teardown":
      return e.teardown;
    case "template":
      return "template" in e ? e.template : "";
    case "timeout":
      return e.timeout;
  }
}
function Bc(e, n) {
  switch (n) {
    case "tags":
      return e.tags.length > 0;
    case "arguments":
      return "arguments" in e && e.arguments.length > 0;
    case "template":
      return "template" in e && e.template !== "";
    case "documentation":
    case "setup":
    case "teardown":
    case "timeout":
      return e[n] !== "";
  }
}
function Kc(e, n, r = null, c = null) {
  const g = Ga(e.steps, e.name, `tc${n}`, "testcase", n, r, c);
  if (Ya(g.nodes, g.edges, `tc${n}`, e, "testcase", n, Dc), e.templateRows && e.templateRows.length > 0) {
    const a = `tc${n}`, _ = g.nodes.find((V) => V.id === `${a}-start`), y = `${a}-template-table`;
    g.nodes.push({
      id: y,
      type: "template-table",
      position: { x: ro, y: ((_ == null ? void 0 : _.position.y) ?? 0) + na + Jn },
      data: {
        templateKeyword: e.template,
        templateRows: e.templateRows,
        section: "testcase",
        sectionIndex: n
      }
    });
    const k = `${a}-end`, A = g.edges.findIndex((V) => V.source === `${a}-start` && V.target === k);
    A >= 0 && g.edges.splice(A, 1), g.edges.push({ id: `${a}-e-start-table`, source: `${a}-start`, target: y }), g.edges.push({ id: `${a}-e-table-end`, source: y, target: k });
  }
  return g;
}
function Hc(e, n, r = null, c = null) {
  const g = Ga(e.steps, e.name, `kw${n}`, "keyword", n, r, c);
  return Ya(g.nodes, g.edges, `kw${n}`, e, "keyword", n, Mc), g;
}
function Ya(e, n, r, c, g, a, _) {
  const y = e.find((V) => V.id === `${r}-start`);
  if (!y) return;
  let k = 0;
  const A = 96;
  for (const V of _) {
    if (!Bc(c, V)) continue;
    const E = Uc(c, V), B = `${r}-${V}`;
    e.push({
      id: B,
      type: "setting-meta",
      position: { x: ro - 280, y: y.position.y + k * A },
      data: {
        kind: V,
        label: xc[V],
        text: E,
        section: g,
        sectionIndex: a
      },
      selectable: !0,
      draggable: !1
    }), n.push({
      id: `${r}-${V}-edge`,
      source: B,
      target: `${r}-start`,
      // Connect side note's RIGHT handle → Start's LEFT handle so the
      // dashed edge runs horizontally between them rather than the
      // default Bottom→Top route (which crossed the whole canvas).
      sourceHandle: "right",
      targetHandle: "left",
      type: "default",
      style: { strokeDasharray: "6 4", stroke: "#9AA5BF", strokeWidth: 1.5 },
      animated: !1
    }), k++;
  }
}
function Oc(e, n = null, r = null) {
  const c = [], g = [];
  for (let a = 0; a < e.testCases.length; a++) {
    const _ = a === 0 ? n : null, { nodes: y, edges: k } = Kc(e.testCases[a], a, _, r), A = a * 500;
    for (const V of y)
      V.position.x += A, c.push(V);
    g.push(...k);
  }
  return { nodes: c, edges: g };
}
function Ic(e, n = null) {
  const r = [], c = [];
  for (let g = 0; g < e.keywords.length; g++) {
    const { nodes: a, edges: _ } = Hc(e.keywords[g], g, null, n), y = g * 500;
    for (const k of a)
      k.position.x += y, r.push(k);
    c.push(..._);
  }
  return { nodes: r, edges: c };
}
function et(e, n) {
  var g, a;
  const r = n.section === "testcase" ? (g = e.testCases[n.sectionIndex]) == null ? void 0 : g.steps : (a = e.keywords[n.sectionIndex]) == null ? void 0 : a.steps;
  if (!r) return;
  const c = r[n.stepIndex];
  c && Object.assign(c, n.step);
}
function Xa(e) {
  switch (e) {
    case "if":
    case "else_if":
    case "else":
    case "for":
    case "while":
    case "try":
    case "except":
    case "finally":
      return "control";
    case "assignment":
      return "assignment";
    case "comment":
      return "comment";
    case "return":
      return "return";
    case "break":
    case "continue":
      return "flow-control";
    default:
      return "keyword";
  }
}
function Wc(e) {
  switch (e.type) {
    case "keyword":
      return e.keyword || "Keyword";
    case "assignment":
      return `${e.returnVars.join(", ")} = ${e.keyword}`;
    case "if":
      return `IF  ${e.condition}`;
    case "else_if":
      return `ELSE IF  ${e.condition}`;
    case "else":
      return "ELSE";
    case "for":
      return `FOR ${e.loopVar} ${e.loopFlavor} ${e.loopValues.join("  ")}`;
    case "while":
      return `WHILE  ${e.condition}`;
    case "try":
      return "TRY";
    case "except":
      return e.exceptPattern ? `EXCEPT  ${e.exceptPattern}` : "EXCEPT";
    case "finally":
      return "FINALLY";
    case "return":
      return `RETURN  ${e.args.join("  ")}`;
    case "break":
      return "BREAK";
    case "continue":
      return "CONTINUE";
    case "comment":
      return `# ${e.comment}`;
    case "var":
      return `VAR ${e.keyword} = ${e.args.join("  ")}`;
    default:
      return e.keyword || e.type;
  }
}
function jc(e, n) {
  if (e.type === "else" || e.type === "else_if") return "false";
  if ((n == null ? void 0 : n.type) === "if" || (n == null ? void 0 : n.type) === "else_if") return "true";
}
function ka(e) {
  return e ? e.split(`
`).length : 0;
}
function Gn(e) {
  return e !== void 0 && e !== "";
}
function qc(e, n, r, c) {
  if (n || r < 0 || r >= e.testCases.length) return null;
  const g = e.testCases[r];
  if (c < 0 || c >= g.steps.length) return null;
  let a = 0, _;
  for (const y of e.preambleLines)
    a += 1, _ = y;
  if (e.settings.length > 0) {
    a > 0 && Gn(_) && (a += 1, _ = ""), a += 1, _ = "*** Settings ***";
    for (const y of e.settings) {
      if (y.key === "#") {
        a += 1, _ = y.value;
        continue;
      }
      const k = y.value && y.value.includes(`
`) ? ka(y.value) : 1;
      a += k, _ = "set";
    }
  }
  if (e.variables.length > 0) {
    a > 0 && Gn(_) && (a += 1, _ = ""), a += 1, _ = "*** Variables ***";
    for (const y of e.variables)
      a += 1, _ = y.name === "#" ? y.value : y.name;
  }
  if (e.testCases.length > 0) {
    a > 0 && Gn(_) && (a += 1, _ = ""), a += 1, _ = "*** Test Cases ***";
    for (let y = 0; y < e.testCases.length; y++) {
      const k = e.testCases[y];
      if (a += 1, k.documentation && (a += ka(k.documentation)), k.tags.length > 0 && (a += 1), k.setup && (a += 1), k.teardown && (a += 1), k.timeout && (a += 1), k.template && (a += 1), y === r)
        return a + c + 1;
      a += k.steps.length, a += 1, _ = "";
    }
  }
  return null;
}
const Za = Object.freeze({
  Click: "Heal Click",
  "Fill Text": "Heal Fill Text",
  "Type Text": "Heal Type Text",
  Hover: "Heal Hover",
  "Press Keys": "Heal Press Keys",
  "Wait For Elements State": "Heal Wait For Elements State",
  "Upload File": "Heal Upload File",
  "Check Checkbox": "Heal Check Checkbox",
  "Uncheck Checkbox": "Heal Uncheck Checkbox",
  "Select Options By": "Heal Select Options By",
  "Get Text": "Heal Get Text",
  "Get Element Count": "Heal Get Element Count",
  "Drag And Drop": "Heal Drag And Drop"
}), zc = Object.freeze(
  Object.fromEntries(
    Object.entries(Za).map(([e, n]) => [n, e])
  )
);
function Wo(e) {
  const n = e.trim();
  return Za[n] ?? null;
}
function aa(e) {
  const n = e.trim();
  return zc[n] ?? null;
}
function jo(e) {
  return aa(e) !== null;
}
function Gc(e) {
  return e.findIndex(
    (n) => n.key === "Library" && n.value === "RoboScopeHeal" && n.args.length === 0
  );
}
function Yc(e) {
  return e.some((n) => n.key === "Library" && n.value === "RoboScopeHeal");
}
function Qa(e) {
  if (Yc(e)) return e;
  const n = { key: "Library", value: "RoboScopeHeal", args: [] };
  return [...e, n];
}
function Ja(e, n) {
  if (n) return e;
  const r = Gc(e);
  return r === -1 ? e : [...e.slice(0, r), ...e.slice(r + 1)];
}
function es(e) {
  let n = 0;
  for (const r of e.testCases)
    for (const c of r.steps) jo(c.keyword) && (n += 1);
  for (const r of e.keywords)
    for (const c of r.steps) jo(c.keyword) && (n += 1);
  return n;
}
function ts(e) {
  return e.settings.some((n) => {
    if (n.key !== "Library") return !1;
    const r = n.value.trim();
    return /^(?:Browser|robotframework[-_]browser(?:[-_]batteries)?)$/i.test(r);
  });
}
function os(e) {
  return e.settings.some(
    (n) => n.key === "Library" && n.value.trim() === "RoboScopeHeal"
  );
}
function Xc(e) {
  let n = 0;
  for (const r of e.testCases)
    for (const c of r.steps) Wo(c.keyword) !== null && (n += 1);
  for (const r of e.keywords)
    for (const c of r.steps) Wo(c.keyword) !== null && (n += 1);
  return n;
}
function Ca(e, n) {
  if (n === "enable") {
    const r = Wo(e.keyword);
    if (r !== null) return { ...e, keyword: r };
  } else {
    const r = aa(e.keyword);
    if (r !== null) return { ...e, keyword: r };
  }
  return e;
}
function Zc(e, n) {
  let r = 0;
  const c = e.testCases.map((y) => {
    let k = !1;
    const A = y.steps.map((V) => {
      const E = Ca(V, n);
      return E !== V && (k = !0, r += 1), E;
    });
    return k ? { ...y, steps: A } : y;
  }), g = e.keywords.map((y) => {
    let k = !1;
    const A = y.steps.map((V) => {
      const E = Ca(V, n);
      return E !== V && (k = !0, r += 1), E;
    });
    return k ? { ...y, steps: A } : y;
  }), a = c.some((y) => y.steps.some((k) => jo(k.keyword))) || g.some((y) => y.steps.some((k) => jo(k.keyword)));
  let _ = e.settings;
  return n === "enable" && a ? _ = Qa(_) : n === "disable" && (_ = Ja(_, a)), {
    form: {
      ...e,
      settings: _,
      testCases: c,
      keywords: g
    },
    changedKeywords: r
  };
}
const Qc = { class: "flow-editor" }, Jc = { class: "flow-section-bar" }, ed = { class: "flow-section-tabs" }, td = { class: "flow-item-tabs" }, od = ["onClick"], nd = ["title"], ad = ["onClick"], sd = ["title"], ld = ["title"], rd = { "aria-hidden": "true" }, id = ["title"], ud = { "aria-hidden": "true" }, cd = ["title"], dd = { "aria-hidden": "true" }, pd = {
  key: 0,
  class: "flow-libraries"
}, fd = {
  key: 0,
  class: "flow-libraries__empty"
}, md = {
  key: 1,
  class: "flow-libraries__group",
  "data-testid": "flow-resources-group"
}, gd = { class: "flow-libraries__group-label" }, vd = { class: "flow-libraries__chips" }, hd = ["title"], yd = ["title", "onClick"], bd = {
  key: 2,
  class: "flow-libraries__group",
  "data-testid": "flow-libraries-group"
}, wd = { class: "flow-libraries__group-label" }, _d = { class: "flow-libraries__chips" }, kd = ["title"], Cd = ["title", "onClick"], $d = { class: "flow-libraries__input-row" }, Td = ["placeholder", "onKeydown"], Ed = ["disabled"], Sd = {
  key: 3,
  class: "flow-libraries__suggestions"
}, Ld = ["onClick"], Vd = {
  key: 1,
  class: "flow-libraries",
  "data-testid": "flow-variables-panel"
}, Ad = { class: "flow-vars__rows" }, Nd = {
  key: 0,
  class: "flow-libraries__empty"
}, Rd = ["value", "onChange"], Fd = ["value", "onChange"], Pd = ["title", "onClick"], Dd = { class: "flow-libraries__input-row" }, Md = ["placeholder", "onKeydown"], xd = ["placeholder", "onKeydown"], Ud = ["disabled"], Bd = {
  key: 0,
  class: "flow-vars__envrefs",
  "data-testid": "flow-env-vars"
}, Kd = ["title"], Hd = ["data-state", "title"], Od = { class: "flow-libraries__chip-name" }, Id = {
  key: 2,
  class: "flow-libraries",
  "data-testid": "flow-suite-settings-panel"
}, Wd = { class: "flow-vars__rows" }, jd = {
  key: 0,
  class: "flow-libraries__empty"
}, qd = { class: "flow-vars__name flow-vars__name--readonly" }, zd = ["value", "onChange"], Gd = ["title", "onClick"], Yd = {
  key: 0,
  class: "flow-libraries__suggestions"
}, Xd = ["onClick"], Zd = {
  key: 3,
  class: "flow-empty"
}, Qd = { class: "flow-empty__actions" }, Jd = {
  key: 4,
  class: "flow-canvas-wrapper"
}, ep = { class: "flow-node-doc-meta__body" }, tp = { class: "flow-node-doc-meta__label" }, op = {
  key: 0,
  class: "flow-node-doc-meta__text"
}, np = {
  key: 1,
  class: "flow-node-doc-meta__text flow-node-doc-meta__text--empty"
}, ap = ["onDragstart"], sp = {
  class: "flow-node-template",
  "data-testid": "flow-template-table"
}, lp = { class: "flow-node-template__head" }, rp = { class: "flow-node-template__kw" }, ip = { class: "flow-node-template__table" }, up = ["value", "onChange"], cp = ["title", "onClick"], dp = { class: "flow-node-template__actions" }, pp = ["onClick"], fp = ["onClick"], mp = ["onDragstart"], gp = ["onDragstart"], vp = {
  key: 0,
  class: "flow-node-return-values"
}, hp = {
  key: 1,
  class: "flow-node-return-empty"
}, yp = ["title"], bp = { class: "flow-detail-header" }, wp = { class: "flow-detail-row flow-settings-add-row" }, _p = ["title", "data-testid", "onClick"], kp = {
  key: 0,
  class: "flow-detail-hint"
}, Cp = ["title"], $p = { class: "flow-detail-header" }, Tp = { class: "flow-detail-actions" }, Ep = ["title"], Sp = { class: "flow-detail-row" }, Lp = ["placeholder"], Vp = ["placeholder"], Ap = { class: "flow-detail-hint" }, Np = ["title"], Rp = { class: "flow-detail-header" }, Fp = { class: "flow-detail-actions" }, Pp = ["title"], Dp = {
  key: 0,
  class: "flow-debug-action-row"
}, Mp = ["title"], xp = {
  key: 0,
  class: "flow-debug-action-error",
  role: "alert"
}, Up = {
  key: 1,
  class: "flow-detail-row"
}, Bp = {
  key: 2,
  class: "flow-detail-row flow-detail-row--heal"
}, Kp = ["for"], Hp = { class: "flow-heal-toggle" }, Op = ["id", "checked"], Ip = { class: "flow-heal-toggle__hint" }, Wp = {
  key: 3,
  class: "flow-detail-row"
}, jp = ["title"], qp = ["title"], zp = { class: "flow-arg-type-icon" }, Gp = { class: "flow-arg-type-label" }, Yp = {
  key: 0,
  class: "flow-arg-type-opt"
}, Xp = {
  key: 1,
  class: "flow-selector-picker-wrap"
}, Zp = {
  key: 0,
  class: "flow-selector-custom-hint"
}, Qp = ["checked", "onChange", "data-testid"], Jp = ["onUpdate:modelValue", "data-testid"], ef = ["value"], tf = {
  value: "",
  disabled: "",
  hidden: ""
}, of = ["value"], nf = ["onUpdate:modelValue", "step", "placeholder"], af = ["onUpdate:modelValue", "placeholder", "title"], sf = ["onUpdate:modelValue", "placeholder"], lf = ["title", "data-testid", "onClick"], rf = ["onClick"], uf = {
  key: 0,
  class: "flow-add-arg-caret"
}, cf = {
  key: 0,
  class: "flow-add-arg-empty"
}, df = ["data-testid", "onClick"], pf = { class: "flow-add-arg-option-name" }, ff = {
  key: 0,
  class: "flow-add-arg-option-default"
}, mf = {
  key: 1,
  class: "flow-add-arg-option-named"
}, gf = {
  class: "flow-add-arg-custom",
  "data-testid": "add-arg-custom"
}, vf = { class: "flow-add-arg-custom-label" }, hf = { class: "flow-add-arg-custom-row" }, yf = ["placeholder", "onKeydown"], bf = ["disabled"], wf = {
  key: 4,
  class: "flow-detail-row"
}, _f = ["onUpdate:modelValue"], kf = ["onClick"], Cf = {
  key: 5,
  class: "flow-detail-row"
}, $f = ["onUpdate:modelValue", "placeholder", "data-testid"], Tf = ["onClick"], Ef = {
  key: 6,
  class: "flow-detail-row"
}, Sf = {
  key: 7,
  class: "flow-detail-row"
}, Lf = {
  key: 8,
  class: "flow-detail-row"
}, Vf = {
  key: 9,
  class: "flow-detail-row"
}, Af = ["onUpdate:modelValue"], Nf = ["onClick"], Rf = {
  key: 10,
  class: "flow-detail-row"
}, Ff = {
  key: 11,
  class: "flow-detail-row"
}, Pf = {
  key: 12,
  class: "flow-detail-row"
}, Df = {
  key: 13,
  class: "flow-detail-row"
}, Mf = {
  key: 14,
  class: "flow-detail-row"
}, xf = { key: 0 }, Uf = {
  key: 0,
  class: "flow-debug-overlay",
  "data-testid": "flow-debug-overlay",
  role: "dialog",
  "aria-modal": "true"
}, Bf = { class: "flow-debug-overlay__inner" }, Kf = 240, Hf = 720, Of = /* @__PURE__ */ je({
  __name: "FlowEditor",
  props: {
    form: {},
    sidecar: {},
    dirty: { type: Boolean }
  },
  emits: ["update:step", "update:sidecar", "add-test-case", "add-keyword", "libraries-changed"],
  setup(e, { emit: n }) {
    const r = e, c = Da(), g = n, { t: a } = mt(), _ = Qn(), y = N([]), k = N([]), { fitView: A, project: V } = fs(), E = N("testcases"), B = N(0), I = O(
      () => r.form.testCases.map((o, s) => o.name || `Test Case ${s + 1}`)
    ), j = O(
      () => r.form.keywords.map((o, s) => o.name || `Keyword ${s + 1}`)
    ), ue = O(() => r.form.testCases.length > 0), M = O(() => r.form.keywords.length > 0);
    O(() => ue.value || M.value);
    const J = O(() => E.value === "testcases" && !ue.value ? "testcases" : E.value === "keywords" && !M.value ? "keywords" : null), ae = N(!1), z = N(""), D = O(() => {
      const o = [];
      return r.form.settings.forEach((s, $) => {
        const K = s.key.toLowerCase();
        K === "library" ? o.push({ idx: $, kind: "library", value: s.value }) : K === "resource" && o.push({ idx: $, kind: "resource", value: s.value });
      }), o;
    }), R = O(
      () => D.value.filter((o) => o.kind === "resource")
    ), H = O(
      () => D.value.filter((o) => o.kind === "library")
    ), Y = O(() => {
      const o = /* @__PURE__ */ new Set(["builtin"]);
      for (const s of D.value)
        s.kind === "library" && o.add(s.value.toLowerCase());
      return o;
    }), U = [
      "BuiltIn",
      "Collections",
      "String",
      "OperatingSystem",
      "Process",
      "DateTime",
      "XML",
      "Dialogs",
      "Screenshot",
      "Telnet",
      "Remote",
      "SeleniumLibrary",
      "Browser",
      "RequestsLibrary",
      "DatabaseLibrary",
      "SSHLibrary",
      "FTPLibrary",
      "ExcelLibrary",
      "JSONLibrary",
      "AppiumLibrary",
      "SwingLibrary",
      "SikuliLibrary",
      "ImapLibrary",
      "ArchiveLibrary",
      "CryptoLibrary",
      "RESTinstance"
    ], Z = O(() => {
      const o = z.value.trim().toLowerCase(), s = new Set(
        D.value.filter(($) => $.kind === "library").map(($) => $.value.toLowerCase())
      );
      return U.filter(($) => s.has($.toLowerCase()) ? !1 : o ? $.toLowerCase().includes(o) : !0).slice(0, 8);
    }), ce = /* @__PURE__ */ new Set([
      "collections",
      "string",
      "datetime",
      "operatingsystem",
      "process",
      "xml",
      "dialogs",
      "screenshot",
      "telnet",
      "remote"
    ]);
    function ke(o) {
      const s = o.trim();
      if (!s) return !1;
      const $ = Xl(s), K = $ ? "Resource" : "Library";
      if (r.form.settings.find(
        (L) => L.key.toLowerCase() === K.toLowerCase() && L.value.trim().toLowerCase() === s.toLowerCase()
      )) return !1;
      r.form.settings.push({ key: K, value: s, args: [] }), z.value = "";
      const C = $ || ce.has(s.toLowerCase());
      return g("libraries-changed", C ? void 0 : s), $ ? "resource" : "library";
    }
    function ee(o, s) {
      _.success(
        a(o === "resource" ? "flowEditor.importAdded.resourceTitle" : "flowEditor.importAdded.libraryTitle"),
        a("flowEditor.importAdded.message", { name: s })
      );
    }
    function W() {
      ke(z.value);
    }
    function oe(o) {
      o < 0 || o >= r.form.settings.length || (r.form.settings.splice(o, 1), g("libraries-changed"));
    }
    const fe = N(!1), be = N(""), Fe = N(""), Pe = O(() => {
      const o = [];
      return r.form.variables.forEach((s, $) => {
        s.name !== "#" && o.push({ idx: $, name: s.name, value: s.value });
      }), o;
    });
    function He(o) {
      const s = o.trim();
      return !s || /^[$@&]\{.*\}$/.test(s) ? s : `\${${s}}`;
    }
    function ie() {
      xe = !0;
    }
    function Ie() {
      const o = He(be.value);
      if (!o) return;
      r.form.variables.some(
        ($) => $.name.toLowerCase() === o.toLowerCase()
      ) || (ie(), r.form.variables.push({ name: o, value: Fe.value.trim() })), be.value = "", Fe.value = "";
    }
    function tt(o, s) {
      if (o < 0 || o >= r.form.variables.length) return;
      const $ = He(s);
      $ && (ie(), r.form.variables[o].name = $);
    }
    function gt(o, s) {
      o < 0 || o >= r.form.variables.length || (ie(), r.form.variables[o].value = s);
    }
    function ut(o) {
      o < 0 || o >= r.form.variables.length || (ie(), r.form.variables.splice(o, 1));
    }
    function Ze(o) {
      const s = r.form.testCases[o];
      return s ? (s.templateRows || (s.templateRows = []), s.templateRows) : [];
    }
    function vt(o, s, $, K) {
      const te = Ze(o);
      if (!(s < 0 || s >= te.length)) {
        for (ie(); te[s].length <= $; ) te[s].push("");
        te[s][$] = K;
      }
    }
    function ze(o) {
      const s = Ze(o), $ = s.length ? Math.max(...s.map((K) => K.length)) : 1;
      s.push(Array.from({ length: $ }, () => "")), Ne(`tc${o}-template-table`);
    }
    function Q(o, s) {
      const $ = Ze(o);
      s >= 0 && s < $.length && $.splice(s, 1), Ne(`tc${o}-template-table`);
    }
    function ot(o) {
      for (const s of Ze(o)) s.push("");
      Ne(`tc${o}-template-table`);
    }
    const pe = O(() => {
      const o = E.value === "testcases" ? r.form.testCases[B.value] : r.form.keywords[B.value];
      if (!o) return [];
      const s = [];
      for (const $ of o.steps)
        s.push(...$.args, $.condition, ...$.loopValues);
      return Fa(s);
    }), se = zo(), me = oa(), Se = O(() => c.scopeId == null ? null : se.resolveEnvironmentId(c.scopeId)), Be = O(() => {
      var o;
      return ((o = me.environments.find((s) => s.id === Se.value)) == null ? void 0 : o.name) ?? "";
    }), ht = O(() => {
      const o = Se.value === null ? void 0 : me.variables[Se.value];
      return o ? new Set(o.map((s) => s.key)) : null;
    });
    Le([() => c.scopeId, Se], ([, o]) => {
      o !== null && me.fetchVariables(o).catch(() => {
      });
    }, { immediate: !0 }), us("envVarKeys", ht);
    const Bt = O(() => Pa(pe.value, ht.value));
    function Vt(o, s) {
      const $ = Be.value;
      if (s === "defined") return a("flowEditor.envVarDefined", { env: $ });
      if (s === "default") return a("flowEditor.envVarDefaultUsed", { env: $ });
      if (s === "missing") return a("flowEditor.envVarMissing", { env: $, name: o });
    }
    const yt = N(!1), to = [
      "Documentation",
      "Metadata",
      "Suite Setup",
      "Suite Teardown",
      "Test Setup",
      "Test Teardown",
      "Force Tags",
      "Default Tags",
      "Test Timeout"
    ], bt = O(() => {
      const o = [];
      return r.form.settings.forEach((s, $) => {
        const K = s.key.toLowerCase();
        K === "library" || K === "resource" || K === "variables" || K === "#" || o.push({ idx: $, key: s.key, value: s.value });
      }), o;
    }), oo = O(() => {
      const o = new Set(
        r.form.settings.map((s) => s.key.toLowerCase())
      );
      return to.filter((s) => !o.has(s.toLowerCase()));
    });
    function Kt(o) {
      r.form.settings.some((s) => s.key.toLowerCase() === o.toLowerCase()) || (ie(), r.form.settings.push({ key: o, value: "", args: [] }));
    }
    function At(o, s) {
      o < 0 || o >= r.form.settings.length || (ie(), r.form.settings[o].value = s);
    }
    function Nt(o) {
      o < 0 || o >= r.form.settings.length || (ie(), r.form.settings.splice(o, 1));
    }
    async function wt() {
      g("add-test-case"), await Xe(), r.form.testCases.length > 0 && (E.value = "testcases", B.value = r.form.testCases.length - 1);
    }
    async function _t() {
      g("add-keyword"), await Xe(), r.form.keywords.length > 0 && (E.value = "keywords", B.value = r.form.keywords.length - 1);
    }
    const F = N(null), w = O(() => !F.value || F.value.type === "setting-meta" || F.value.type === "start" || F.value.type === "template-table" ? null : F.value.data), P = O(
      () => {
        var o;
        return ((o = F.value) == null ? void 0 : o.type) === "start";
      }
    ), G = [
      "documentation",
      "tags",
      "setup",
      "teardown",
      "template",
      "timeout"
    ], ge = [
      "documentation",
      "arguments",
      "tags",
      "setup",
      "teardown",
      "timeout"
    ], Ve = {
      documentation: "[Documentation]",
      tags: "[Tags]",
      arguments: "[Arguments]",
      setup: "[Setup]",
      teardown: "[Teardown]",
      template: "[Template]",
      timeout: "[Timeout]"
    };
    function nt() {
      return E.value === "testcases" ? r.form.testCases[B.value] : r.form.keywords[B.value];
    }
    function Ht(o) {
      const s = nt();
      return s ? o === "tags" ? s.tags.length === 0 : o === "arguments" ? "arguments" in s ? s.arguments.length === 0 : !1 : o === "template" ? "template" in s ? !s.template : !1 : !s[o] : !1;
    }
    const Go = O(
      () => E.value === "testcases" ? G : ge
    ), Ae = O(() => {
      if (!F.value || F.value.type !== "setting-meta") return null;
      const o = F.value.data;
      return { kind: o.kind, section: o.section, sectionIndex: o.sectionIndex };
    });
    function io() {
      if (!Ae.value) return;
      const { section: o, sectionIndex: s } = Ae.value;
      return o === "testcase" ? r.form.testCases[s] : r.form.keywords[s];
    }
    const Ge = N("");
    function To() {
      const o = io();
      if (!o || !Ae.value) return "";
      const s = Ae.value.kind;
      return s === "tags" ? o.tags.join(", ") : s === "arguments" ? "arguments" in o ? o.arguments.join(", ") : "" : s === "template" ? "template" in o ? o.template : "" : o[s];
    }
    Le(
      () => Ae.value && [
        Ae.value.section,
        Ae.value.sectionIndex,
        Ae.value.kind
      ].join("|"),
      (o) => {
        o && (Ge.value = To());
      },
      { immediate: !0 }
    );
    function Yo() {
      const o = io();
      if (!o || !Ae.value) return;
      const s = Ge.value, $ = Ae.value.kind;
      $ === "tags" ? o.tags = uo(s) : $ === "arguments" && "arguments" in o ? o.arguments = uo(s) : $ === "template" && "template" in o ? o.template = s : ($ === "documentation" || $ === "setup" || $ === "teardown" || $ === "timeout") && (o[$] = s), Ne();
    }
    function uo(o) {
      return o.split(/,\s*|\n+/).map((s) => s.trim()).filter((s) => s.length > 0);
    }
    function En() {
      if (!Ae.value) return;
      const o = io();
      if (!o) return;
      const s = Ae.value.kind;
      s === "tags" ? o.tags = [] : s === "arguments" && "arguments" in o ? o.arguments = [] : s === "template" && "template" in o ? o.template = "" : (s === "documentation" || s === "setup" || s === "teardown" || s === "timeout") && (o[s] = ""), F.value = null, Ne();
    }
    function Sn(o) {
      const s = nt();
      s && (o === "tags" ? s.tags = [""] : o === "arguments" && "arguments" in s ? s.arguments = [""] : o === "template" && "template" in s ? s.template = " " : (o === "documentation" || o === "setup" || o === "teardown" || o === "timeout") && (s[o] = " "), Ne(), Xe(() => {
        const $ = E.value === "testcases" ? `tc${B.value}` : `kw${B.value}`, K = y.value.find((te) => te.id === `${$}-${o}`);
        K && (F.value = K);
      }));
    }
    const ct = N(300), Rt = N(!1);
    function Ft(o) {
      o.preventDefault(), Rt.value = !0;
      const s = o.clientX, $ = ct.value;
      function K(C) {
        const L = s - C.clientX, he = Math.min(Hf, Math.max(Kf, $ + L));
        ct.value = he;
      }
      function te() {
        Rt.value = !1, window.removeEventListener("pointermove", K), window.removeEventListener("pointerup", te), window.removeEventListener("pointercancel", te);
      }
      window.addEventListener("pointermove", K), window.addEventListener("pointerup", te), window.addEventListener("pointercancel", te);
    }
    const { argsByName: co, getKeywordInfo: Xo, getParsedArgs: Ot } = Tn();
    function It(o) {
      if (o.type !== "keyword" && o.type !== "assignment" || o.args.length > 0) return;
      const s = Ot(o.keyword);
      if (s)
        for (const $ of s)
          if ($.kind === "positional") o.args.push("");
          else break;
    }
    const Ln = O(() => {
      const o = /* @__PURE__ */ new Map(), s = ($) => {
        for (const K of $) {
          if (K.type !== "keyword" && K.type !== "assignment" || !K.keyword) continue;
          const te = Xo(K.keyword), C = te == null ? void 0 : te.library;
          C && o.set(C, (o.get(C) ?? 0) + 1);
        }
      };
      for (const $ of r.form.testCases) s($.steps);
      for (const $ of r.form.keywords) s($.steps);
      return o;
    }), Vn = O(() => D.value.length), An = O(() => {
      let o = 0;
      for (const s of r.form.testCases) o += s.steps.length;
      for (const s of r.form.keywords) o += s.steps.length;
      return o;
    });
    function kt() {
      const o = r.sidecar ?? null, s = co.value;
      if (E.value === "testcases") {
        const $ = Oc(r.form, o, s);
        y.value = $.nodes, k.value = $.edges;
      } else {
        const $ = Ic(r.form, s);
        y.value = $.nodes, k.value = $.edges;
      }
    }
    const Eo = O(() => {
      const o = E.value === "testcases" ? `tc${B.value}-` : `kw${B.value}-`;
      return y.value.filter((s) => s.id.startsWith(o));
    }), Pt = O(() => {
      const o = Eo.value;
      if (Zt.value === null) return o;
      const s = Hn(Zt.value), $ = {
        id: "__drop-indicator__",
        type: "drop-indicator",
        position: { x: ro - 50, y: s - 2 },
        data: {},
        draggable: !1,
        selectable: !1
      };
      return [...o, $];
    }), po = O(() => {
      const o = E.value === "testcases" ? `tc${B.value}-` : `kw${B.value}-`;
      return k.value.filter((s) => s.id.startsWith(o));
    });
    let xe = !1;
    Le([() => r.form, E], () => {
      const o = xe;
      xe = !1, o || (B.value = 0, F.value = null), kt(), o || Xe(() => {
        A({ padding: 0.3 }), setTimeout(() => A({ padding: 0.3 }), 100);
      });
    }, { deep: !0 }), Le(() => r.sidecar, () => {
      kt();
    }), Le(co, () => {
      kt();
    }), Le(B, () => {
      F.value = null, Xe(() => {
        A({ padding: 0.3 }), setTimeout(() => A({ padding: 0.3 }), 100);
      });
    });
    function So(o) {
      if (o.key !== "Backspace" && o.key !== "Delete" || !F.value || !w.value) return;
      const s = o.target;
      if (s) {
        const $ = s.tagName;
        if ($ === "INPUT" || $ === "TEXTAREA" || $ === "SELECT" || s.isContentEditable) return;
      }
      o.preventDefault(), Po();
    }
    qo(() => {
      !ue.value && M.value && (E.value = "keywords"), kt(), setTimeout(() => A({ padding: 0.3 }), 200), setTimeout(() => A({ padding: 0.3 }), 500), window.addEventListener("keydown", So);
    }), Ho(() => {
      window.removeEventListener("keydown", So);
    });
    function Lo(o) {
      if (o.node.type === "end") {
        F.value = null;
        return;
      }
      F.value = o.node;
    }
    function Nn() {
      F.value = null;
    }
    function Ne(o) {
      var $;
      const s = o ?? (($ = F.value) == null ? void 0 : $.id);
      xe = !0, kt(), s && Xe(() => {
        const K = y.value.find((te) => te.id === s);
        F.value = K || null;
      });
    }
    function Vo(o) {
      return `${E.value === "testcases" ? `tc${B.value}` : `kw${B.value}`}-step-${o}`;
    }
    function Ue() {
      w.value && (et(r.form, w.value), Ne(), g("update:step", w.value));
    }
    function Zo(o) {
      if (!w.value) return;
      const s = w.value.recording;
      if (!s) return;
      const $ = s.selector_candidates[o];
      $ && (ba(w.value.step, s) && !window.confirm(
        a("flowEditor.selector.replaceCustomConfirm", {
          current: w.value.step.args[0] ?? "",
          next: $.value
        })
      ) || Tc(w.value.step, s, o) && (et(r.form, w.value), Ne(), g("update:step", w.value), r.sidecar && g("update:sidecar", r.sidecar)));
    }
    function Rn(o) {
      if (!w.value) return;
      const s = w.value.recording;
      if (!s) return;
      const $ = s.selector_candidates[o.index];
      if ($) {
        if ($.value = o.value, $.strategy = o.strategy, $.quality_score = 50, $.verified_unique = !1, o.effective !== void 0 && ($.effective_override = o.effective === "" ? null : o.effective), o.index === s.active_candidate_index) {
          const K = ya(s, $);
          w.value.step.args.length === 0 ? w.value.step.args.push(K) : w.value.step.args[0] = K, et(r.form, w.value);
        }
        Ne(), g("update:step", w.value), r.sidecar && g("update:sidecar", r.sidecar);
      }
    }
    function Fn(o) {
      if (!w.value) return;
      const s = w.value.recording;
      if (!s) return;
      const $ = {
        strategy: o.strategy,
        value: o.value,
        quality_score: 50,
        verified_unique: !1,
        effective_override: o.effective !== void 0 && o.effective !== "" ? o.effective : null
      };
      s.selector_candidates.push($);
      const K = s.selector_candidates.length - 1;
      s.active_candidate_index = K;
      const te = ya(s, $);
      w.value.step.args.length === 0 ? w.value.step.args.push(te) : w.value.step.args[0] = te, et(r.form, w.value), Ne(), g("update:step", w.value), r.sidecar && g("update:sidecar", r.sidecar);
    }
    const Ao = O(() => {
      var s;
      const o = (s = w.value) == null ? void 0 : s.recording;
      return !!o && o.selector_candidates.length > 0;
    }), Pn = O(() => {
      const o = w.value;
      return o != null && o.recording ? ba(o.step, o.recording) : !1;
    });
    function fo(o) {
      var te;
      const s = w.value;
      if (!s) return null;
      const $ = s.step.args[o] ?? "", K = /^([A-Za-z_][\w]*)\s*=/.exec($);
      if (K && s.argSpecs) {
        const C = s.argSpecs.find((L) => L.name === K[1]);
        if (C) return { spec: C, viaName: !0 };
      }
      return (te = s.argSpecs) != null && te[o] ? { spec: s.argSpecs[o], viaName: !1 } : null;
    }
    function Dn(o) {
      var $;
      const s = fo(o);
      return s != null && s.viaName ? s.spec.name : Na((($ = w.value) == null ? void 0 : $.argSpecs) ?? null, o, a);
    }
    function Qo(o) {
      w.value && (w.value.step.keyword = o);
    }
    const no = O(() => {
      const o = w.value;
      if (!o || o.stepType !== "keyword" && o.stepType !== "assignment") return "hidden";
      const s = (o.step.keyword ?? "").trim(), $ = jo(s), K = Wo(s) !== null;
      return !$ && !K || !ts(r.form) && !os(r.form) ? "hidden" : $ ? "on" : "off";
    });
    function Ct(o) {
      const s = w.value;
      if (!s) return;
      const $ = s.step.keyword.trim(), K = o ? Wo($) : aa($);
      if (K === null) return;
      s.step.keyword = K, Ue();
      const te = es(r.form) > 0, C = o ? Qa(r.form.settings) : Ja(r.form.settings, te);
      C !== r.form.settings && r.form.settings.splice(0, r.form.settings.length, ...C);
    }
    const mo = N(!1), go = O(() => {
      var o;
      return ((o = w.value) == null ? void 0 : o.step.keyword) ?? "";
    }), dt = O(() => {
      const o = w.value;
      return !o || o.stepType !== "keyword" && o.stepType !== "assignment" ? !1 : (o.step.keyword ?? "").trim().length > 0;
    });
    function $t() {
      dt.value && (mo.value = !0);
    }
    function Qe(o) {
      const s = fo(o);
      return (s == null ? void 0 : s.spec.defaultValue) != null && s.spec.defaultValue !== "" ? s.spec.defaultValue : a("flowEditor.argLabels.fallback", { n: o + 1 });
    }
    function at(o) {
      const s = fo(o);
      return bn((s == null ? void 0 : s.spec.type) ?? null);
    }
    function Jo(o) {
      const s = at(o);
      if (s.raw)
        return s.raw + (s.optional ? a("flowEditor.argTypes.optionalSuffix") : "");
    }
    const ao = N(/* @__PURE__ */ new Set());
    function No(o) {
      var s;
      return `${((s = F.value) == null ? void 0 : s.id) ?? "?"}#${o}`;
    }
    function vo(o) {
      return ao.value.has(No(o));
    }
    function en(o) {
      const s = No(o), $ = new Set(ao.value);
      $.has(s) ? $.delete(s) : $.add(s), ao.value = $;
    }
    Le(F, () => {
      ao.value = /* @__PURE__ */ new Set();
    });
    function Wt(o) {
      var C;
      if (vo(o)) return "text";
      const s = at(o).control;
      if (s === "text") return s;
      const $ = ((C = w.value) == null ? void 0 : C.step.args[o]) ?? "", K = /^([A-Za-z_][\w]*)\s*=(.*)$/.exec($), te = K ? K[2] : $;
      return Bs(te) ? "text" : s;
    }
    const Ro = /^([A-Za-z_][\w]*)\s*=(.*)$/;
    function Mn(o, s) {
      if (!w.value) return;
      const $ = s.target.checked, K = w.value.step.args[o] ?? "", te = Ro.exec(K);
      w.value.step.args[o] = te ? `${te[1]}=${ua($)}` : ua($), Ue();
    }
    function tn(o) {
      var C, L;
      const s = ((C = w.value) == null ? void 0 : C.step.args[o]) ?? "", $ = Ro.exec(s), K = $ ? $[2] : s;
      if (K) return ia(K);
      const te = (L = fo(o)) == null ? void 0 : L.spec.defaultValue;
      return ia(te ?? "");
    }
    const st = N(!1), jt = N(""), De = O(() => {
      const o = w.value;
      if (!(o != null && o.argSpecs)) return [];
      const s = o.step.args, $ = new Set(o.argSpecs.map((C) => C.name)), K = /* @__PURE__ */ new Set();
      for (let C = 0; C < s.length; C++) {
        const L = s[C], he = /^([A-Za-z_][\w]*)\s*=/.exec(L);
        if (he && $.has(he[1]))
          K.add(he[1]);
        else {
          const Me = o.argSpecs[C];
          Me && (Me.kind === "positional" || Me.kind === "optional") && K.add(Me.name);
        }
      }
      const te = [];
      for (let C = 0; C < o.argSpecs.length; C++) {
        const L = o.argSpecs[C];
        L.kind !== "positional" && L.kind !== "optional" || !L.name || K.has(L.name) || te.push({
          name: L.name,
          defaultValue: L.defaultValue,
          isNextPositional: C === s.length
        });
      }
      return te;
    }), qt = N(null), pt = N(null), zt = N({
      top: "0px",
      left: "0px",
      minWidth: "200px"
    });
    function on() {
      var L;
      const o = qt.value;
      if (!o) return;
      const s = o.getBoundingClientRect(), $ = Math.max(s.width, 200), K = Math.max(8, window.innerWidth - $ - 8), te = ((L = pt.value) == null ? void 0 : L.offsetHeight) || 240;
      let C = s.bottom + 4;
      if (C + te + 8 > window.innerHeight) {
        const he = s.top - te - 4;
        C = he >= 8 ? he : Math.max(8, window.innerHeight - te - 8);
      }
      zt.value = {
        top: `${C}px`,
        left: `${Math.min(s.left, K)}px`,
        minWidth: `${$}px`
      };
    }
    function Tt() {
      st.value = !st.value, st.value && Xe(on);
    }
    function Gt(o) {
      if (!w.value) return;
      let s;
      o.isNextPositional ? s = "" : o.defaultValue != null && o.defaultValue !== "" ? s = `${o.name}=${o.defaultValue}` : s = `${o.name}=`, w.value.step.args.push(s), et(r.form, w.value), Ne(), st.value = !1;
    }
    function nn() {
      if (!w.value) return;
      const o = jt.value.trim();
      o && (w.value.step.args.push(o), et(r.form, w.value), Ne(), jt.value = "", st.value = !1);
    }
    let Dt = !1;
    function Yt(o) {
      const s = o.target;
      s instanceof Element && (s.closest(".flow-add-arg-wrap, .flow-add-arg-popover") || (st.value = !1));
    }
    function Mt() {
      st.value && on();
    }
    function ho() {
      Dt || (document.addEventListener("click", Yt), window.addEventListener("scroll", Mt, !0), window.addEventListener("resize", Mt), Dt = !0);
    }
    function Te() {
      Dt && (document.removeEventListener("click", Yt), window.removeEventListener("scroll", Mt, !0), window.removeEventListener("resize", Mt), Dt = !1);
    }
    Le(st, (o) => {
      o ? ho() : (Te(), jt.value = "");
    }), Ho(Te);
    function Xt(o) {
      w.value && (w.value.step.args.splice(o, 1), et(r.form, w.value), Ne());
    }
    function lt() {
      if (!w.value) return;
      const o = w.value.step;
      o.returnVars.push("${var}"), o.type === "keyword" && (o.type = "assignment"), et(r.form, w.value), Ne();
    }
    function Fo(o) {
      if (!w.value) return;
      const s = w.value.step;
      s.returnVars.splice(o, 1), s.returnVars.length === 0 && s.type === "assignment" && (s.type = "keyword"), et(r.form, w.value), Ne();
    }
    function xn() {
      w.value && (w.value.step.loopValues.push(""), et(r.form, w.value), Ne());
    }
    function yo(o) {
      w.value && (w.value.step.loopValues.splice(o, 1), et(r.form, w.value), Ne());
    }
    function an() {
      w.value && (w.value.step.args.push(""), et(r.form, w.value), Ne());
    }
    function $e(o) {
      w.value && (w.value.step.args.splice(o, 1), et(r.form, w.value), Ne());
    }
    function bo(o) {
      return {
        type: o,
        keyword: "",
        args: [],
        returnVars: [],
        condition: "",
        loopVar: "",
        loopFlavor: "",
        loopValues: [],
        exceptPattern: "",
        exceptVar: "",
        varScope: "",
        comment: ""
      };
    }
    function sn(o) {
      return o === "try" ? [bo("except"), bo("end")] : o === "if" || o === "for" || o === "while" ? [bo("end")] : [];
    }
    function Un(o, s) {
      var te, C;
      if (s) {
        const L = ke(s);
        L && ee(L, s);
      }
      It(o);
      const $ = E.value === "testcases" ? (te = r.form.testCases[B.value]) == null ? void 0 : te.steps : (C = r.form.keywords[B.value]) == null ? void 0 : C.steps;
      if (!$) return;
      const K = w.value ? w.value.stepIndex + 1 : $.length;
      $.splice(K, 0, o, ...sn(o.type)), Ne(Vo(K));
    }
    function rt() {
      var o, s;
      return E.value === "testcases" ? ((o = r.form.testCases[B.value]) == null ? void 0 : o.steps) ?? null : ((s = r.form.keywords[B.value]) == null ? void 0 : s.steps) ?? null;
    }
    function Bn() {
      if (!w.value) return;
      const o = rt();
      if (!o) return;
      const s = w.value.stepIndex;
      if (s <= 0) return;
      const $ = o[s];
      o[s] = o[s - 1], o[s - 1] = $, w.value.stepIndex = s - 1, Ne(Vo(s - 1)), g("update:step", w.value);
    }
    function Kn() {
      if (!w.value) return;
      const o = rt();
      if (!o) return;
      const s = w.value.stepIndex;
      if (s >= o.length - 1) return;
      const $ = o[s];
      o[s] = o[s + 1], o[s + 1] = $, w.value.stepIndex = s + 1, Ne(Vo(s + 1)), g("update:step", w.value);
    }
    function Po() {
      if (!w.value) return;
      const o = rt();
      o && (o.splice(w.value.stepIndex, 1), F.value = null, xe = !0, kt());
    }
    const Zt = N(null), ln = N(null);
    function rn() {
      return Eo.value.filter((o) => o.type !== "start" && o.type !== "end").sort((o, s) => o.position.y - s.position.y);
    }
    function Do(o) {
      const s = rn();
      if (s.length === 0) return 0;
      for (let $ = 0; $ < s.length; $++) {
        const K = s[$].position.y, te = s[$].data, C = K + Ko(te.step) / 2;
        if (o < C) return $;
      }
      return s.length;
    }
    function xt(o) {
      const s = ln.value;
      if (!s) return 0;
      const $ = s.getBoundingClientRect();
      return V({ x: o.clientX - $.left, y: o.clientY - $.top }).y;
    }
    function Hn(o) {
      const s = rn();
      if (s.length === 0) return Lc;
      if (o <= 0) return s[0].position.y - 25;
      if (o >= s.length) {
        const C = s[s.length - 1], L = C.data;
        return C.position.y + Ko(L.step) + 25;
      }
      const $ = s[o - 1], K = s[o];
      return ($.position.y + Ko($.data.step) + K.position.y) / 2;
    }
    function Mo(o) {
      var K, te;
      const s = (K = o.dataTransfer) == null ? void 0 : K.getData("application/rf-keyword"), $ = (te = o.dataTransfer) == null ? void 0 : te.getData("application/rf-control");
      return s ? {
        type: "keyword",
        keyword: s,
        args: [],
        returnVars: [],
        condition: "",
        loopVar: "${item}",
        loopFlavor: "IN",
        loopValues: [],
        exceptPattern: "",
        exceptVar: "",
        varScope: "",
        comment: ""
      } : $ && wc($) ? {
        type: $,
        keyword: "",
        args: [],
        returnVars: [],
        condition: $ === "if" || $ === "while" ? "${condition}" : "",
        loopVar: $ === "for" ? "${item}" : "",
        loopFlavor: $ === "for" ? "IN" : "",
        loopValues: $ === "for" ? ["@{list}"] : [],
        exceptPattern: "",
        exceptVar: "",
        varScope: "",
        comment: ""
      } : null;
    }
    function un(o, s) {
      const $ = rt();
      if (!$) return;
      let K = 0, te = 0;
      for (let C = 0; C < $.length; C++) {
        if ($[C].type === "end") {
          K++;
          continue;
        }
        if (te === s) break;
        te++, K++;
      }
      $.splice(K, 0, o, ...sn(o.type)), xe = !0, kt();
    }
    function wo(o) {
      var L, he;
      o.preventDefault(), Zt.value = null;
      const s = (L = o.dataTransfer) == null ? void 0 : L.getData("application/rf-reorder");
      if (s !== void 0 && s !== "") {
        const Me = parseInt(s, 10), Ce = xt(o), Et = Do(Ce);
        On(Me, Et);
        return;
      }
      const $ = Mo(o);
      if (!$) return;
      const K = ((he = o.dataTransfer) == null ? void 0 : he.getData("application/rf-library")) || "";
      if (K) {
        const Me = ke(K);
        Me && ee(Me, K);
      }
      It($);
      const te = xt(o), C = Do(te);
      un($, C);
    }
    function cn(o) {
      o.preventDefault(), o.dataTransfer.dropEffect = "copy";
      const s = xt(o);
      Zt.value = Do(s);
    }
    function xo() {
      Zt.value = null;
    }
    function so(o, s) {
      var C;
      const $ = s.data, K = rt();
      if (!K) return;
      let te = 0;
      for (let L = 0; L < K.length; L++)
        if (K[L].type !== "end") {
          if (L === $.stepIndex) break;
          te++;
        }
      (C = o.dataTransfer) == null || C.setData("application/rf-reorder", String(te)), o.dataTransfer.effectAllowed = "move";
    }
    function On(o, s) {
      const $ = rt();
      if (!$) return;
      const K = [];
      for (let Et = 0; Et < $.length; Et++)
        $[Et].type !== "end" && K.push(Et);
      if (o < 0 || o >= K.length) return;
      const te = K[o];
      let C;
      if (s >= K.length ? C = $.length : (s <= o, C = K[s]), te === C || te + 1 === C) return;
      const L = $[te], Me = ["if", "for", "while", "try"].includes(L.type) && te + 1 < $.length && $[te + 1].type === "end" ? $.splice(te, 2) : $.splice(te, 1);
      let Ce;
      C > te ? Ce = C - Me.length : Ce = C, Ce = Math.max(0, Math.min(Ce, $.length)), $.splice(Ce, 0, ...Me), xe = !0, kt();
    }
    function Qt(o, s) {
      const $ = Eo.value.find((K) => K.id === s);
      $ && so(o, $);
    }
    const dn = hc(), Ke = ja(), Jt = O(() => {
      const o = w.value;
      return !(!o || o.section !== "testcase" || !dn.hasMinRole("runner") || ![
        "keyword",
        "assignment",
        "return",
        "if",
        "else_if",
        "else",
        "for",
        "while",
        "try",
        "except",
        "finally"
      ].includes(o.step.type) || !c.filePath || c.scopeId == null);
    }), ft = N(!1), Je = N(null), lo = N(null);
    function In() {
      var te;
      const o = w.value;
      if (!o || o.section !== "testcase" || !c.filePath || c.scopeId == null) return null;
      const s = r.form.testCases[o.sectionIndex];
      if (!s) return null;
      const $ = ((te = c.filePath) == null ? void 0 : te.toLowerCase().endsWith(".resource")) ?? !1, K = qc(r.form, $, o.sectionIndex, o.stepIndex);
      return K === null || K < 1 ? null : {
        file: c.filePath,
        testName: s.name,
        line: K,
        scopeId: c.scopeId
      };
    }
    async function f() {
      if (lo.value = null, r.dirty) {
        ft.value = !0;
        return;
      }
      const o = In();
      if (!o) {
        lo.value = a("flowEditor.debug.unresolvedLine");
        return;
      }
      const s = Ke.classifyStepClick(o.file, o.line);
      if (s === "same") {
        S.value = !0;
        return;
      }
      if (s === "different") {
        Je.value = {
          fromLine: Ke.breakpointLine ?? 0,
          toLine: o.line,
          payload: o
        };
        return;
      }
      await h(o);
    }
    async function h(o) {
      try {
        await Ke.startFromStep({
          file: o.file,
          test_name: o.testName,
          line: o.line,
          scope_id: o.scopeId
        }) !== null && (S.value = !0);
      } catch (s) {
        lo.value = Co(s, a("flowEditor.debug.startFailed"));
      }
    }
    async function d() {
      await Ke.installPrereqAndRetry() && Ke.isActive && (S.value = !0);
    }
    function v() {
      Ke.cancelPrereq();
    }
    async function l() {
      const o = Je.value;
      if (o) {
        Je.value = null;
        try {
          await Ke.stop();
        } catch {
        }
        await h(o.payload);
      }
    }
    const S = N(!1);
    function b() {
      S.value = !1;
    }
    return (o, s) => {
      var $, K, te;
      return i(), u("div", Qc, [
        t("div", Jc, [
          t("div", ed, [
            t("button", {
              class: ne(["flow-section-tab", { active: E.value === "testcases" }]),
              onClick: s[0] || (s[0] = (C) => E.value = "testcases")
            }, m(p(a)("robotEditor.testCasesSection")) + " (" + m(r.form.testCases.length) + ") ", 3),
            t("button", {
              class: ne(["flow-section-tab", { active: E.value === "keywords" }]),
              onClick: s[1] || (s[1] = (C) => E.value = "keywords")
            }, m(p(a)("robotEditor.keywordsSection")) + " (" + m(r.form.keywords.length) + ") ", 3)
          ]),
          t("div", td, [
            E.value === "testcases" ? (i(), u(x, { key: 0 }, [
              (i(!0), u(x, null, X(I.value, (C, L) => (i(), u("button", {
                key: "tc" + L,
                class: ne(["flow-item-tab", { active: B.value === L }]),
                onClick: (he) => B.value = L
              }, m(C), 11, od))), 128)),
              t("button", {
                type: "button",
                class: "flow-item-tab flow-item-tab--add",
                title: p(a)("flowEditor.addTestCaseTitle"),
                "data-testid": "flow-add-test-case",
                onClick: wt
              }, "+ " + m(p(a)("flowEditor.addTestCase")), 9, nd)
            ], 64)) : (i(), u(x, { key: 1 }, [
              (i(!0), u(x, null, X(j.value, (C, L) => (i(), u("button", {
                key: "kw" + L,
                class: ne(["flow-item-tab", { active: B.value === L }]),
                onClick: (he) => B.value = L
              }, m(C), 11, ad))), 128)),
              t("button", {
                type: "button",
                class: "flow-item-tab flow-item-tab--add",
                title: p(a)("flowEditor.addKeywordTitle"),
                "data-testid": "flow-add-keyword",
                onClick: _t
              }, "+ " + m(p(a)("flowEditor.addKeyword")), 9, sd)
            ], 64))
          ]),
          t("button", {
            type: "button",
            class: ne(["flow-libs-toggle", { active: ae.value }]),
            title: p(a)("flowEditor.librariesTitle"),
            "data-testid": "flow-libraries-toggle",
            onClick: s[2] || (s[2] = (C) => ae.value = !ae.value)
          }, [
            we(" 📚 " + m(p(a)("flowEditor.libraries")) + " (" + m(D.value.length) + ") ", 1),
            t("span", rd, m(ae.value ? "▴" : "▾"), 1)
          ], 10, ld),
          t("button", {
            type: "button",
            class: ne(["flow-libs-toggle", { active: fe.value }]),
            title: p(a)("flowEditor.variablesTitle"),
            "data-testid": "flow-variables-toggle",
            onClick: s[3] || (s[3] = (C) => fe.value = !fe.value)
          }, [
            we(" 📦 " + m(p(a)("flowEditor.variables")) + " (" + m(Pe.value.length) + ") ", 1),
            t("span", ud, m(fe.value ? "▴" : "▾"), 1)
          ], 10, id),
          t("button", {
            type: "button",
            class: ne(["flow-libs-toggle", { active: yt.value }]),
            title: p(a)("flowEditor.suiteSettingsTitle"),
            "data-testid": "flow-suite-settings-toggle",
            onClick: s[4] || (s[4] = (C) => yt.value = !yt.value)
          }, [
            we(" ⚙️ " + m(p(a)("flowEditor.suiteSettings")) + " (" + m(bt.value.length) + ") ", 1),
            t("span", dd, m(yt.value ? "▴" : "▾"), 1)
          ], 10, cd)
        ]),
        ae.value ? (i(), u("div", pd, [
          D.value.length === 0 ? (i(), u("span", fd, m(p(a)("flowEditor.librariesNone")), 1)) : T("", !0),
          R.value.length ? (i(), u("div", md, [
            t("div", gd, m(p(a)("flowEditor.resourcePanelLabel")) + " (" + m(R.value.length) + ")", 1),
            t("div", vd, [
              (i(!0), u(x, null, X(R.value, (C) => (i(), u("span", {
                key: C.idx,
                class: "flow-libraries__chip flow-libraries__chip--resource"
              }, [
                s[33] || (s[33] = t("span", { class: "flow-libraries__chip-kind" }, "R", -1)),
                t("span", {
                  class: "flow-libraries__chip-name",
                  title: C.value
                }, m(C.value), 9, hd),
                t("button", {
                  type: "button",
                  class: "flow-libraries__chip-remove",
                  title: p(a)("flowEditor.libraryRemoveTitle", { name: C.value }),
                  onClick: (L) => oe(C.idx),
                  "data-testid": "flow-library-remove"
                }, "×", 8, yd)
              ]))), 128))
            ])
          ])) : T("", !0),
          H.value.length ? (i(), u("div", bd, [
            t("div", wd, m(p(a)("flowEditor.librariesPanelLabel")) + " (" + m(H.value.length) + ")", 1),
            t("div", _d, [
              (i(!0), u(x, null, X(H.value, (C) => (i(), u("span", {
                key: C.idx,
                class: "flow-libraries__chip"
              }, [
                s[34] || (s[34] = t("span", { class: "flow-libraries__chip-kind" }, "L", -1)),
                t("span", {
                  class: "flow-libraries__chip-name",
                  title: C.value
                }, m(C.value), 9, kd),
                t("button", {
                  type: "button",
                  class: "flow-libraries__chip-remove",
                  title: p(a)("flowEditor.libraryRemoveTitle", { name: C.value }),
                  onClick: (L) => oe(C.idx),
                  "data-testid": "flow-library-remove"
                }, "×", 8, Cd)
              ]))), 128))
            ])
          ])) : T("", !0),
          t("div", $d, [
            q(t("input", {
              "onUpdate:modelValue": s[5] || (s[5] = (C) => z.value = C),
              type: "text",
              class: "flow-libraries__input",
              placeholder: p(a)("flowEditor.libraryInputPlaceholder"),
              autocomplete: "off",
              spellcheck: "false",
              "data-testid": "flow-library-input",
              onKeydown: Ye(re(W, ["prevent"]), ["enter"])
            }, null, 40, Td), [
              [le, z.value]
            ]),
            t("button", {
              type: "button",
              class: "flow-libraries__add",
              disabled: !z.value.trim(),
              "data-testid": "flow-library-add",
              onClick: W
            }, "+ " + m(p(a)("flowEditor.libraryAdd")), 9, Ed)
          ]),
          Z.value.length ? (i(), u("div", Sd, [
            (i(!0), u(x, null, X(Z.value, (C) => (i(), u("button", {
              key: C,
              type: "button",
              class: "flow-libraries__suggestion",
              onClick: (L) => ke(C)
            }, m(C), 9, Ld))), 128))
          ])) : T("", !0)
        ])) : T("", !0),
        fe.value ? (i(), u("div", Vd, [
          t("div", Ad, [
            Pe.value.length === 0 ? (i(), u("span", Nd, m(p(a)("flowEditor.variablesNone")), 1)) : T("", !0),
            (i(!0), u(x, null, X(Pe.value, (C) => (i(), u("div", {
              key: C.idx,
              class: "flow-vars__row"
            }, [
              t("input", {
                class: "flow-vars__name",
                value: C.name,
                spellcheck: "false",
                autocomplete: "off",
                "data-testid": "flow-variable-name",
                onChange: (L) => tt(C.idx, L.target.value)
              }, null, 40, Rd),
              t("input", {
                class: "flow-vars__value",
                value: C.value,
                spellcheck: "false",
                autocomplete: "off",
                "data-testid": "flow-variable-value",
                onChange: (L) => gt(C.idx, L.target.value)
              }, null, 40, Fd),
              t("button", {
                type: "button",
                class: "flow-libraries__chip-remove",
                title: p(a)("flowEditor.variableRemoveTitle", { name: C.name }),
                "data-testid": "flow-variable-remove",
                onClick: (L) => ut(C.idx)
              }, "×", 8, Pd)
            ]))), 128))
          ]),
          t("div", Dd, [
            q(t("input", {
              "onUpdate:modelValue": s[6] || (s[6] = (C) => be.value = C),
              type: "text",
              class: "flow-vars__name",
              placeholder: p(a)("flowEditor.variableNamePlaceholder"),
              autocomplete: "off",
              spellcheck: "false",
              "data-testid": "flow-variable-new-name",
              onKeydown: Ye(re(Ie, ["prevent"]), ["enter"])
            }, null, 40, Md), [
              [le, be.value]
            ]),
            q(t("input", {
              "onUpdate:modelValue": s[7] || (s[7] = (C) => Fe.value = C),
              type: "text",
              class: "flow-vars__value",
              placeholder: p(a)("flowEditor.variableValuePlaceholder"),
              autocomplete: "off",
              spellcheck: "false",
              "data-testid": "flow-variable-new-value",
              onKeydown: Ye(re(Ie, ["prevent"]), ["enter"])
            }, null, 40, xd), [
              [le, Fe.value]
            ]),
            t("button", {
              type: "button",
              class: "flow-libraries__add",
              disabled: !be.value.trim(),
              "data-testid": "flow-variable-add",
              onClick: Ie
            }, "+ " + m(p(a)("flowEditor.variableAdd")), 9, Ud)
          ]),
          pe.value.length ? (i(), u("div", Bd, [
            t("span", {
              class: "flow-vars__envrefs-title",
              title: ht.value ? p(a)("flowEditor.envVarCheckedAgainst", { env: Be.value }) : void 0
            }, m(p(a)("flowEditor.envVarsUsed")), 9, Kd),
            (i(!0), u(x, null, X(Bt.value, ({ ref: C, state: L }) => (i(), u("span", {
              key: C.name,
              class: ne(["flow-libraries__chip", { "flow-vars__envref--missing": L === "missing" }]),
              "data-state": L,
              title: Vt(C.name, L),
              "data-testid": "flow-env-var"
            }, [
              t("span", Od, "%" + m("{") + m(C.name) + m(C.default !== null ? "=" + C.default : "") + m("}"), 1)
            ], 10, Hd))), 128))
          ])) : T("", !0)
        ])) : T("", !0),
        yt.value ? (i(), u("div", Id, [
          t("div", Wd, [
            bt.value.length === 0 ? (i(), u("span", jd, m(p(a)("flowEditor.suiteSettingsNone")), 1)) : T("", !0),
            (i(!0), u(x, null, X(bt.value, (C) => (i(), u("div", {
              key: C.idx,
              class: "flow-vars__row"
            }, [
              t("span", qd, m(C.key), 1),
              t("input", {
                class: "flow-vars__value",
                value: C.value,
                spellcheck: "false",
                autocomplete: "off",
                "data-testid": "flow-suite-setting-value",
                onChange: (L) => At(C.idx, L.target.value)
              }, null, 40, zd),
              t("button", {
                type: "button",
                class: "flow-libraries__chip-remove",
                title: p(a)("flowEditor.suiteSettingRemoveTitle", { name: C.key }),
                "data-testid": "flow-suite-setting-remove",
                onClick: (L) => Nt(C.idx)
              }, "×", 8, Gd)
            ]))), 128))
          ]),
          oo.value.length ? (i(), u("div", Yd, [
            (i(!0), u(x, null, X(oo.value, (C) => (i(), u("button", {
              key: C,
              type: "button",
              class: "flow-libraries__suggestion",
              "data-testid": "flow-suite-setting-add",
              onClick: (L) => Kt(C)
            }, "+ " + m(C), 9, Xd))), 128))
          ])) : T("", !0)
        ])) : T("", !0),
        J.value ? (i(), u("div", Zd, [
          t("p", null, m(J.value === "testcases" ? p(a)("flowEditor.noTestCasesYet") : p(a)("flowEditor.noKeywordsYet")), 1),
          t("div", Qd, [
            J.value === "testcases" ? (i(), u("button", {
              key: 0,
              type: "button",
              class: "flow-empty__cta",
              "data-testid": "flow-empty-add-test-case",
              onClick: wt
            }, "+ " + m(p(a)("flowEditor.addTestCase")), 1)) : T("", !0),
            J.value === "keywords" ? (i(), u("button", {
              key: 1,
              type: "button",
              class: "flow-empty__cta",
              "data-testid": "flow-empty-add-keyword",
              onClick: _t
            }, "+ " + m(p(a)("flowEditor.addKeyword")), 1)) : T("", !0)
          ])
        ])) : (i(), u("div", Jd, [
          ve(pi, {
            "scope-id": p(c).scopeId ?? void 0,
            "file-path": p(c).filePath,
            "imported-libraries": Y.value,
            "usage-counts": Ln.value,
            "file-import-count": Vn.value,
            "file-step-count": An.value,
            onAddNode: Un
          }, null, 8, ["scope-id", "file-path", "imported-libraries", "usage-counts", "file-import-count", "file-step-count"]),
          t("div", {
            ref_key: "canvasRef",
            ref: ln,
            class: "flow-canvas"
          }, [
            ve(p(ms), {
              onDrop: wo,
              onDragover: cn,
              onDragleave: xo,
              nodes: Pt.value,
              edges: po.value,
              "default-viewport": { zoom: 0.9, x: 0, y: 0 },
              "min-zoom": 0.2,
              "max-zoom": 2,
              "nodes-draggable": !1,
              "fit-view-on-init": "",
              onNodeClick: Lo,
              onPaneClick: Nn
            }, {
              "node-keyword": _e((C) => {
                var L;
                return [
                  ve(da, Uo(C, {
                    "reorder-enabled": !0,
                    selected: ((L = F.value) == null ? void 0 : L.id) === C.id,
                    onReorderDragStart: (he) => Qt(he, C.id)
                  }), null, 16, ["selected", "onReorderDragStart"])
                ];
              }),
              "node-assignment": _e((C) => {
                var L;
                return [
                  ve(da, Uo(C, {
                    "reorder-enabled": !0,
                    selected: ((L = F.value) == null ? void 0 : L.id) === C.id,
                    onReorderDragStart: (he) => Qt(he, C.id)
                  }), null, 16, ["selected", "onReorderDragStart"])
                ];
              }),
              "node-control": _e((C) => {
                var L;
                return [
                  ve(ul, Uo(C, {
                    "reorder-enabled": !0,
                    selected: ((L = F.value) == null ? void 0 : L.id) === C.id,
                    onReorderDragStart: (he) => Qt(he, C.id)
                  }), null, 16, ["selected", "onReorderDragStart"])
                ];
              }),
              "node-control-frame": _e((C) => [
                ve(dl, cs(ds(C)), null, 16)
              ]),
              "node-start": _e((C) => [
                ve(pa, Uo(C, { type: "start" }), null, 16)
              ]),
              "node-end": _e((C) => [
                ve(pa, Uo(C, { type: "end" }), null, 16)
              ]),
              "node-setting-meta": _e((C) => {
                var L;
                return [
                  t("div", {
                    class: ne(["flow-node-doc-meta", [
                      `flow-node-doc-meta--${C.data.kind}`,
                      { "flow-node--selected": ((L = F.value) == null ? void 0 : L.id) === C.id }
                    ]])
                  }, [
                    t("div", ep, [
                      t("span", tp, m(C.data.label), 1),
                      C.data.text && C.data.text.trim() ? (i(), u("p", op, m(C.data.text), 1)) : (i(), u("p", np, m(p(a)("flowEditor.settingMeta.emptyHint")), 1))
                    ]),
                    ve(p(St), {
                      type: "source",
                      position: p(Lt).Right,
                      id: "right",
                      style: { opacity: "0", "pointer-events": "none" }
                    }, null, 8, ["position"])
                  ], 2)
                ];
              }),
              "node-comment": _e((C) => {
                var L;
                return [
                  t("div", {
                    class: ne(["flow-node-comment", { "flow-node--selected": ((L = F.value) == null ? void 0 : L.id) === C.id }])
                  }, [
                    t("div", {
                      class: "flow-drag-handle",
                      draggable: "true",
                      onMousedown: s[8] || (s[8] = re(() => {
                      }, ["stop"])),
                      onDragstart: re((he) => Qt(he, C.id), ["stop"])
                    }, "☰", 40, ap),
                    t("span", null, m(C.data.label), 1)
                  ], 2)
                ];
              }),
              "node-template-table": _e((C) => [
                t("div", sp, [
                  ve(p(St), {
                    type: "target",
                    position: p(Lt).Top
                  }, null, 8, ["position"]),
                  t("div", lp, [
                    s[35] || (s[35] = t("span", { class: "flow-node-template__badge" }, "[Template]", -1)),
                    t("span", rp, m(C.data.templateKeyword || "—"), 1)
                  ]),
                  t("table", ip, [
                    t("tbody", null, [
                      (i(!0), u(x, null, X(C.data.templateRows, (L, he) => (i(), u("tr", { key: he }, [
                        (i(!0), u(x, null, X(L, (Me, Ce) => (i(), u("td", { key: Ce }, [
                          t("input", {
                            class: "flow-node-template__cell",
                            value: Me,
                            spellcheck: "false",
                            "data-testid": "flow-template-cell",
                            onMousedown: s[9] || (s[9] = re(() => {
                            }, ["stop"])),
                            onChange: (Et) => vt(C.data.sectionIndex, he, Ce, Et.target.value)
                          }, null, 40, up)
                        ]))), 128)),
                        t("td", null, [
                          t("button", {
                            type: "button",
                            class: "flow-node-template__rm",
                            "data-testid": "flow-template-remove-row",
                            title: p(a)("flowEditor.templateRemoveRow"),
                            onMousedown: s[10] || (s[10] = re(() => {
                            }, ["stop"])),
                            onClick: re((Me) => Q(C.data.sectionIndex, he), ["stop"])
                          }, "×", 40, cp)
                        ])
                      ]))), 128))
                    ])
                  ]),
                  t("div", dp, [
                    t("button", {
                      type: "button",
                      "data-testid": "flow-template-add-row",
                      onMousedown: s[11] || (s[11] = re(() => {
                      }, ["stop"])),
                      onClick: re((L) => ze(C.data.sectionIndex), ["stop"])
                    }, "+ " + m(p(a)("flowEditor.templateAddRow")), 41, pp),
                    t("button", {
                      type: "button",
                      "data-testid": "flow-template-add-col",
                      onMousedown: s[12] || (s[12] = re(() => {
                      }, ["stop"])),
                      onClick: re((L) => ot(C.data.sectionIndex), ["stop"])
                    }, "+ " + m(p(a)("flowEditor.templateAddColumn")), 41, fp)
                  ]),
                  ve(p(St), {
                    type: "source",
                    position: p(Lt).Bottom
                  }, null, 8, ["position"])
                ])
              ]),
              "node-flow-control": _e((C) => {
                var L;
                return [
                  t("div", {
                    class: ne(["flow-node-flowctrl", { "flow-node--selected": ((L = F.value) == null ? void 0 : L.id) === C.id }])
                  }, [
                    t("div", {
                      class: "flow-drag-handle",
                      draggable: "true",
                      onMousedown: s[13] || (s[13] = re(() => {
                      }, ["stop"])),
                      onDragstart: re((he) => Qt(he, C.id), ["stop"])
                    }, "☰", 40, mp),
                    t("span", null, m(C.data.label), 1)
                  ], 2)
                ];
              }),
              "node-return": _e((C) => {
                var L, he, Me;
                return [
                  t("div", {
                    class: ne(["flow-node-return-block", { "flow-node--selected": ((L = F.value) == null ? void 0 : L.id) === C.id }])
                  }, [
                    t("div", {
                      class: "flow-drag-handle",
                      draggable: "true",
                      onMousedown: s[14] || (s[14] = re(() => {
                      }, ["stop"])),
                      onDragstart: re((Ce) => Qt(Ce, C.id), ["stop"])
                    }, "☰", 40, gp),
                    s[36] || (s[36] = t("span", { class: "flow-node-return-label" }, "↵ RETURN", -1)),
                    (Me = (he = C.data.step) == null ? void 0 : he.args) != null && Me.length ? (i(), u("div", vp, [
                      (i(!0), u(x, null, X(C.data.step.args, (Ce, Et) => (i(), u("span", {
                        key: Et,
                        class: "flow-node-return-value"
                      }, m(Ce), 1))), 128))
                    ])) : (i(), u("span", hp, m(p(a)("flowEditor.returnNoValue")), 1))
                  ], 2)
                ];
              }),
              "node-drop-indicator": _e(() => [...s[37] || (s[37] = [
                t("div", { class: "flow-drop-indicator" }, [
                  t("div", { class: "flow-drop-dot" }),
                  t("div", { class: "flow-drop-line" }),
                  t("div", { class: "flow-drop-dot" })
                ], -1)
              ])]),
              default: _e(() => [
                ve(p(gs)),
                ve(p(vs)),
                ve(p(hs))
              ]),
              _: 1
            }, 8, ["nodes", "edges"])
          ], 512),
          P.value ? (i(), u("div", {
            key: 0,
            class: ne(["flow-detail-panel", { "flow-detail-panel--resizing": Rt.value }]),
            style: Ee({ width: ct.value + "px" })
          }, [
            t("div", {
              class: "flow-detail-resizer",
              role: "separator",
              "aria-orientation": "vertical",
              title: p(a)("flowEditor.resizePanelHint"),
              onPointerdown: Ft
            }, null, 40, yp),
            t("div", bp, [
              t("h4", null, m(E.value === "testcases" ? p(a)("flowEditor.testCaseSettings") : p(a)("flowEditor.keywordSettings")), 1)
            ]),
            t("div", wp, [
              (i(!0), u(x, null, X(Go.value, (C) => (i(), u(x, { key: C }, [
                Ht(C) ? (i(), u("button", {
                  key: 0,
                  type: "button",
                  class: "flow-btn-add",
                  title: p(a)(`flowEditor.settingMeta.${C}.addTitle`),
                  "data-testid": `flow-add-${C}`,
                  onClick: (L) => Sn(C)
                }, "+ " + m(Ve[C]), 9, _p)) : T("", !0)
              ], 64))), 128)),
              Go.value.every((C) => !Ht(C)) ? (i(), u("p", kp, m(p(a)("flowEditor.settingMeta.allPresent")), 1)) : T("", !0)
            ])
          ], 6)) : Ae.value ? (i(), u("div", {
            key: 1,
            class: ne(["flow-detail-panel", { "flow-detail-panel--resizing": Rt.value }]),
            style: Ee({ width: ct.value + "px" })
          }, [
            t("div", {
              class: "flow-detail-resizer",
              role: "separator",
              "aria-orientation": "vertical",
              title: p(a)("flowEditor.resizePanelHint"),
              onPointerdown: Ft
            }, null, 40, Cp),
            t("div", $p, [
              t("h4", null, m(Ve[Ae.value.kind]), 1),
              t("div", Tp, [
                t("button", {
                  class: "flow-action-btn flow-action-delete",
                  onClick: En,
                  title: p(a)(`flowEditor.settingMeta.${Ae.value.kind}.removeTitle`)
                }, "×", 8, Ep)
              ])
            ]),
            t("div", Sp, [
              t("label", null, m(p(a)(`flowEditor.settingMeta.${Ae.value.kind}.label`)), 1),
              Ae.value.kind === "documentation" ? q((i(), u("textarea", {
                key: 0,
                "onUpdate:modelValue": s[15] || (s[15] = (C) => Ge.value = C),
                class: "flow-input flow-textarea",
                rows: "8",
                placeholder: p(a)("flowEditor.settingMeta.documentation.placeholder"),
                onBlur: Yo
              }, null, 40, Lp)), [
                [le, Ge.value]
              ]) : q((i(), u("input", {
                key: 1,
                "onUpdate:modelValue": s[16] || (s[16] = (C) => Ge.value = C),
                type: "text",
                class: "flow-input",
                placeholder: p(a)(`flowEditor.settingMeta.${Ae.value.kind}.placeholder`),
                onBlur: Yo
              }, null, 40, Vp)), [
                [le, Ge.value]
              ])
            ]),
            t("p", Ap, m(p(a)(`flowEditor.settingMeta.${Ae.value.kind}.hint`)), 1)
          ], 6)) : w.value ? (i(), u("div", {
            key: 2,
            class: ne(["flow-detail-panel", { "flow-detail-panel--resizing": Rt.value }]),
            style: Ee({ width: ct.value + "px" }),
            onDrop: re(wo, ["stop"]),
            onDragover: re(cn, ["stop", "prevent"]),
            onDragleave: re(xo, ["stop"])
          }, [
            t("div", {
              class: "flow-detail-resizer",
              role: "separator",
              "aria-orientation": "vertical",
              title: p(a)("flowEditor.resizePanelHint"),
              onPointerdown: Ft
            }, null, 40, Np),
            t("div", Rp, [
              t("h4", null, m(w.value.stepType.toUpperCase().replace("_", " ")), 1),
              t("div", Fp, [
                t("button", {
                  class: "flow-action-btn",
                  onClick: Bn,
                  title: "Move up"
                }, "↑"),
                t("button", {
                  class: "flow-action-btn",
                  onClick: Kn,
                  title: "Move down"
                }, "↓"),
                dt.value ? (i(), u("button", {
                  key: 0,
                  class: "flow-action-btn flow-action-info",
                  title: p(a)("flowEditor.docModal.button"),
                  "data-testid": "kw-doc-info-btn",
                  onClick: $t
                }, "ⓘ", 8, Pp)) : T("", !0),
                t("button", {
                  class: "flow-action-btn flow-action-delete",
                  onClick: Po,
                  title: "Delete"
                }, "×")
              ])
            ]),
            Jt.value ? (i(), u("div", Dp, [
              t("button", {
                class: "flow-debug-step-btn",
                title: p(a)("flowEditor.debug.btnTitle"),
                "data-testid": "flow-debug-step-btn",
                onClick: f
              }, [
                s[38] || (s[38] = t("span", { "aria-hidden": "true" }, "▶", -1)),
                we(" " + m(p(a)("flowEditor.debug.btnLabel")), 1)
              ], 8, Mp),
              lo.value ? (i(), u("p", xp, m(lo.value), 1)) : T("", !0)
            ])) : T("", !0),
            ["keyword", "assignment"].includes(w.value.stepType) ? (i(), u("div", Up, [
              t("label", null, m(p(a)("flowEditor.keyword")), 1),
              ve(bi, {
                value: w.value.step.keyword,
                "onUpdate:value": Qo,
                onSelect: Ue
              }, null, 8, ["value"])
            ])) : T("", !0),
            no.value !== "hidden" ? (i(), u("div", Bp, [
              t("label", {
                for: "heal-toggle-" + (($ = F.value) == null ? void 0 : $.id)
              }, m(p(a)("flowEditor.heal.toggleLabel")), 9, Kp),
              t("div", Hp, [
                t("input", {
                  id: "heal-toggle-" + ((K = F.value) == null ? void 0 : K.id),
                  type: "checkbox",
                  checked: no.value === "on",
                  "data-testid": "flow-step-heal-toggle",
                  onChange: s[17] || (s[17] = (C) => Ct(C.target.checked))
                }, null, 40, Op),
                t("span", Ip, m(p(a)("flowEditor.heal.toggleHint")), 1)
              ])
            ])) : T("", !0),
            ["keyword", "assignment"].includes(w.value.stepType) ? (i(), u("div", Wp, [
              t("label", null, m(p(a)("flowEditor.arguments")), 1),
              (i(!0), u(x, null, X(w.value.step.args, (C, L) => {
                var he, Me;
                return i(), u("div", {
                  key: L,
                  class: "flow-arg-row"
                }, [
                  t("span", {
                    class: "flow-arg-name-inline",
                    title: ((Me = (he = w.value.argSpecs) == null ? void 0 : he[L]) == null ? void 0 : Me.type) ?? void 0
                  }, m(Dn(L)) + ":", 9, jp),
                  !(L === 0 && Ao.value && w.value.recording) && at(L).labelKey !== "flowEditor.argTypes.unknown" ? (i(), u("span", {
                    key: 0,
                    class: "flow-arg-type-chip",
                    title: Jo(L),
                    "data-testid": "arg-type-chip"
                  }, [
                    t("span", zp, m(at(L).icon), 1),
                    t("span", Gp, m(p(a)(at(L).labelKey)), 1),
                    at(L).optional ? (i(), u("span", Yp, "?")) : T("", !0)
                  ], 8, qp)) : T("", !0),
                  L === 0 && Ao.value && w.value.recording ? (i(), u("div", Xp, [
                    ve(bu, {
                      command: w.value.recording,
                      "onUpdate:activeIndex": Zo,
                      "onUpdate:candidate": Rn,
                      "onAdd:candidate": Fn
                    }, null, 8, ["command"]),
                    Pn.value ? (i(), u("span", Zp, m(p(a)("flowEditor.selector.customValueHint")), 1)) : T("", !0)
                  ])) : (i(), u(x, { key: 2 }, [
                    Wt(L) === "checkbox" ? (i(), u("input", {
                      key: 0,
                      type: "checkbox",
                      class: "flow-input-checkbox",
                      checked: tn(L),
                      onChange: (Ce) => Mn(L, Ce),
                      "data-testid": `arg-bool-${L}`
                    }, null, 40, Qp)) : Wt(L) === "select" ? q((i(), u("select", {
                      key: 1,
                      "onUpdate:modelValue": (Ce) => w.value.step.args[L] = Ce,
                      class: "flow-input flow-input-sm",
                      "data-testid": `arg-select-${L}`,
                      onChange: Ue
                    }, [
                      w.value.step.args[L] && !(at(L).choices ?? []).includes(w.value.step.args[L]) ? (i(), u("option", {
                        key: 0,
                        value: w.value.step.args[L]
                      }, m(w.value.step.args[L]) + " (" + m(p(a)("flowEditor.argTypes.customValue")) + ")", 9, ef)) : T("", !0),
                      t("option", tf, m(Qe(L)), 1),
                      (i(!0), u(x, null, X(at(L).choices ?? [], (Ce) => (i(), u("option", {
                        key: Ce,
                        value: Ce
                      }, m(Ce), 9, of))), 128))
                    ], 40, Jp)), [
                      [it, w.value.step.args[L]]
                    ]) : Wt(L) === "integer" || Wt(L) === "number" ? q((i(), u("input", {
                      key: 2,
                      "onUpdate:modelValue": (Ce) => w.value.step.args[L] = Ce,
                      type: "number",
                      step: Wt(L) === "integer" ? "1" : "any",
                      inputmode: "decimal",
                      class: "flow-input flow-input-sm",
                      placeholder: Qe(L),
                      onChange: Ue
                    }, null, 40, nf)), [
                      [le, w.value.step.args[L]]
                    ]) : Wt(L) === "duration" ? q((i(), u("input", {
                      key: 3,
                      "onUpdate:modelValue": (Ce) => w.value.step.args[L] = Ce,
                      class: "flow-input flow-input-sm",
                      placeholder: Qe(L) || p(a)("flowEditor.argTypes.durationHint"),
                      title: p(a)("flowEditor.argTypes.durationHint"),
                      onChange: Ue
                    }, null, 40, af)), [
                      [le, w.value.step.args[L]]
                    ]) : q((i(), u("input", {
                      key: 4,
                      "onUpdate:modelValue": (Ce) => w.value.step.args[L] = Ce,
                      class: "flow-input flow-input-sm",
                      placeholder: Qe(L),
                      onChange: Ue
                    }, null, 40, sf)), [
                      [le, w.value.step.args[L]]
                    ]),
                    at(L).control !== "text" ? (i(), u("button", {
                      key: 5,
                      type: "button",
                      class: "flow-input-toggle",
                      title: vo(L) ? p(a)("flowEditor.argTypes.toggleBackToTyped") : p(a)("flowEditor.argTypes.toggleToText"),
                      "data-testid": `arg-text-toggle-${L}`,
                      onClick: (Ce) => en(L)
                    }, m(vo(L) ? "⌨" : "{}"), 9, lf)) : T("", !0)
                  ], 64)),
                  t("button", {
                    class: "flow-btn-remove",
                    onClick: (Ce) => Xt(L)
                  }, "×", 8, rf)
                ]);
              }), 128)),
              t("div", {
                ref_key: "addArgTriggerRef",
                ref: qt,
                class: "flow-add-arg-wrap"
              }, [
                t("button", {
                  class: "flow-btn-add",
                  type: "button",
                  onClick: Tt
                }, [
                  we(" + " + m(p(a)("flowEditor.addArg")) + " ", 1),
                  De.value.length > 0 ? (i(), u("span", uf, "▾")) : T("", !0)
                ]),
                (i(), Ut(yn, { to: "body" }, [
                  st.value ? (i(), u("div", {
                    key: 0,
                    ref_key: "addArgPopoverRef",
                    ref: pt,
                    class: "flow-add-arg-popover",
                    role: "listbox",
                    "data-testid": "add-arg-popover",
                    style: Ee(zt.value)
                  }, [
                    De.value.length === 0 ? (i(), u("div", cf, m(p(a)("flowEditor.addArgPicker.noUnused")), 1)) : T("", !0),
                    (i(!0), u(x, null, X(De.value, (C) => (i(), u("button", {
                      key: C.name,
                      type: "button",
                      class: "flow-add-arg-option",
                      "data-testid": `add-arg-option-${C.name}`,
                      onClick: (L) => Gt(C)
                    }, [
                      t("span", pf, m(C.name), 1),
                      C.defaultValue !== null ? (i(), u("span", ff, " = " + m(C.defaultValue), 1)) : T("", !0),
                      C.isNextPositional ? T("", !0) : (i(), u("span", mf, m(p(a)("flowEditor.addArgPicker.namedHint")), 1))
                    ], 8, df))), 128)),
                    t("div", gf, [
                      t("span", vf, m(p(a)("flowEditor.addArgPicker.customLabel")), 1),
                      t("div", hf, [
                        q(t("input", {
                          "onUpdate:modelValue": s[18] || (s[18] = (C) => jt.value = C),
                          type: "text",
                          class: "flow-add-arg-custom-input",
                          placeholder: p(a)("flowEditor.addArgPicker.customPlaceholder"),
                          autocomplete: "off",
                          spellcheck: "false",
                          "data-testid": "add-arg-custom-input",
                          onKeydown: [
                            Ye(re(nn, ["prevent"]), ["enter"]),
                            s[19] || (s[19] = re(() => {
                            }, ["stop"]))
                          ]
                        }, null, 40, yf), [
                          [le, jt.value]
                        ]),
                        t("button", {
                          type: "button",
                          class: "flow-add-arg-custom-add",
                          disabled: !jt.value.trim(),
                          "data-testid": "add-arg-custom-add",
                          onClick: nn
                        }, m(p(a)("flowEditor.addArgPicker.customAdd")), 9, bf)
                      ])
                    ])
                  ], 4)) : T("", !0)
                ]))
              ], 512)
            ])) : T("", !0),
            ["keyword", "assignment"].includes(w.value.stepType) ? (i(), u("div", wf, [
              t("label", null, m(p(a)("flowEditor.returnVars")), 1),
              (i(!0), u(x, null, X(w.value.step.returnVars, (C, L) => (i(), u("div", {
                key: L,
                class: "flow-arg-row"
              }, [
                q(t("input", {
                  "onUpdate:modelValue": (he) => w.value.step.returnVars[L] = he,
                  class: "flow-input flow-input-sm",
                  onChange: Ue
                }, null, 40, _f), [
                  [le, w.value.step.returnVars[L]]
                ]),
                t("button", {
                  class: "flow-btn-remove",
                  onClick: (he) => Fo(L)
                }, "×", 8, kf)
              ]))), 128)),
              t("button", {
                class: "flow-btn-add",
                onClick: lt
              }, "+ " + m(p(a)("flowEditor.addVar")), 1)
            ])) : T("", !0),
            w.value.stepType === "return" ? (i(), u("div", Cf, [
              t("label", null, m(p(a)("flowEditor.returnValues")), 1),
              (i(!0), u(x, null, X(w.value.step.args, (C, L) => (i(), u("div", {
                key: L,
                class: "flow-arg-row"
              }, [
                q(t("input", {
                  "onUpdate:modelValue": (he) => w.value.step.args[L] = he,
                  class: "flow-input flow-input-sm",
                  placeholder: p(a)("flowEditor.returnValuePlaceholder"),
                  "data-testid": `return-value-${L}`,
                  onChange: Ue
                }, null, 40, $f), [
                  [le, w.value.step.args[L]]
                ]),
                t("button", {
                  class: "flow-btn-remove",
                  onClick: (he) => $e(L)
                }, "×", 8, Tf)
              ]))), 128)),
              t("button", {
                type: "button",
                class: "flow-btn-add",
                "data-testid": "add-return-value",
                onClick: an
              }, "+ " + m(p(a)("flowEditor.addValue")), 1)
            ])) : T("", !0),
            ["if", "else_if", "while"].includes(w.value.stepType) ? (i(), u("div", Ef, [
              t("label", null, m(p(a)("flowEditor.condition")), 1),
              q(t("input", {
                "onUpdate:modelValue": s[20] || (s[20] = (C) => w.value.step.condition = C),
                class: "flow-input",
                onChange: Ue
              }, null, 544), [
                [le, w.value.step.condition]
              ])
            ])) : T("", !0),
            w.value.stepType === "for" ? (i(), u("div", Sf, [
              t("label", null, m(p(a)("flowEditor.loopVar")), 1),
              q(t("input", {
                "onUpdate:modelValue": s[21] || (s[21] = (C) => w.value.step.loopVar = C),
                class: "flow-input",
                onChange: Ue
              }, null, 544), [
                [le, w.value.step.loopVar]
              ])
            ])) : T("", !0),
            w.value.stepType === "for" ? (i(), u("div", Lf, [
              t("label", null, m(p(a)("flowEditor.loopFlavor")), 1),
              q(t("select", {
                "onUpdate:modelValue": s[22] || (s[22] = (C) => w.value.step.loopFlavor = C),
                class: "flow-input",
                onChange: Ue
              }, [...s[39] || (s[39] = [
                t("option", null, "IN", -1),
                t("option", null, "IN RANGE", -1),
                t("option", null, "IN ENUMERATE", -1),
                t("option", null, "IN ZIP", -1)
              ])], 544), [
                [it, w.value.step.loopFlavor]
              ])
            ])) : T("", !0),
            w.value.stepType === "for" ? (i(), u("div", Vf, [
              t("label", null, m(p(a)("flowEditor.loopValues")), 1),
              (i(!0), u(x, null, X(w.value.step.loopValues, (C, L) => (i(), u("div", {
                key: L,
                class: "flow-arg-row"
              }, [
                q(t("input", {
                  "onUpdate:modelValue": (he) => w.value.step.loopValues[L] = he,
                  class: "flow-input flow-input-sm",
                  onChange: Ue
                }, null, 40, Af), [
                  [le, w.value.step.loopValues[L]]
                ]),
                t("button", {
                  class: "flow-btn-remove",
                  onClick: (he) => yo(L)
                }, "×", 8, Nf)
              ]))), 128)),
              t("button", {
                class: "flow-btn-add",
                onClick: xn
              }, "+ " + m(p(a)("flowEditor.addValue")), 1)
            ])) : T("", !0),
            w.value.stepType === "except" ? (i(), u("div", Rf, [
              t("label", null, m(p(a)("flowEditor.exceptPattern")), 1),
              q(t("input", {
                "onUpdate:modelValue": s[23] || (s[23] = (C) => w.value.step.exceptPattern = C),
                class: "flow-input",
                onChange: Ue
              }, null, 544), [
                [le, w.value.step.exceptPattern]
              ])
            ])) : T("", !0),
            w.value.stepType === "except" ? (i(), u("div", Ff, [
              t("label", null, m(p(a)("flowEditor.exceptVar")), 1),
              q(t("input", {
                "onUpdate:modelValue": s[24] || (s[24] = (C) => w.value.step.exceptVar = C),
                class: "flow-input",
                placeholder: "AS ${error}",
                onChange: Ue
              }, null, 544), [
                [le, w.value.step.exceptVar]
              ])
            ])) : T("", !0),
            w.value.stepType === "var" ? (i(), u("div", Pf, [
              t("label", null, m(p(a)("flowEditor.varName")), 1),
              q(t("input", {
                "onUpdate:modelValue": s[25] || (s[25] = (C) => w.value.step.keyword = C),
                class: "flow-input",
                onChange: Ue
              }, null, 544), [
                [le, w.value.step.keyword]
              ])
            ])) : T("", !0),
            w.value.stepType === "var" ? (i(), u("div", Df, [
              t("label", null, m(p(a)("flowEditor.varScope")), 1),
              q(t("select", {
                "onUpdate:modelValue": s[26] || (s[26] = (C) => w.value.step.varScope = C),
                class: "flow-input",
                onChange: Ue
              }, [...s[40] || (s[40] = [
                t("option", { value: "" }, "default", -1),
                t("option", null, "LOCAL", -1),
                t("option", null, "TEST", -1),
                t("option", null, "TASK", -1),
                t("option", null, "SUITE", -1),
                t("option", null, "GLOBAL", -1)
              ])], 544), [
                [it, w.value.step.varScope]
              ])
            ])) : T("", !0),
            w.value.stepType === "comment" ? (i(), u("div", Mf, [
              t("label", null, m(p(a)("flowEditor.comment")), 1),
              q(t("input", {
                "onUpdate:modelValue": s[27] || (s[27] = (C) => w.value.step.comment = C),
                class: "flow-input",
                onChange: Ue
              }, null, 544), [
                [le, w.value.step.comment]
              ])
            ])) : T("", !0)
          ], 38)) : T("", !0)
        ])),
        ve(xi, {
          modelValue: mo.value,
          "onUpdate:modelValue": s[28] || (s[28] = (C) => mo.value = C),
          keyword: go.value,
          "scope-id": p(c).scopeId ?? void 0
        }, null, 8, ["modelValue", "keyword", "scope-id"]),
        ve(Io, {
          modelValue: ft.value,
          "onUpdate:modelValue": s[30] || (s[30] = (C) => ft.value = C),
          title: p(a)("flowEditor.debug.unsavedHintTitle"),
          size: "sm",
          "data-testid": "flow-debug-unsaved-modal"
        }, {
          footer: _e(() => [
            ve(Oe, {
              variant: "ghost",
              onClick: s[29] || (s[29] = (C) => ft.value = !1)
            }, {
              default: _e(() => [
                we(m(p(a)("editor.common.close")), 1)
              ]),
              _: 1
            })
          ]),
          default: _e(() => [
            t("p", null, m(p(a)("flowEditor.debug.unsavedHint")), 1)
          ]),
          _: 1
        }, 8, ["modelValue", "title"]),
        ve(Io, {
          "model-value": Je.value !== null,
          title: p(a)("flowEditor.debug.confirmReplaceTitle"),
          size: "sm",
          "data-testid": "flow-debug-confirm-modal",
          "onUpdate:modelValue": s[32] || (s[32] = (C) => {
            C || (Je.value = null);
          })
        }, {
          footer: _e(() => [
            ve(Oe, {
              variant: "ghost",
              onClick: s[31] || (s[31] = (C) => Je.value = null)
            }, {
              default: _e(() => [
                we(m(p(a)("editor.common.cancel")), 1)
              ]),
              _: 1
            }),
            ve(Oe, {
              variant: "primary",
              "data-testid": "flow-debug-confirm-restart-btn",
              onClick: l
            }, {
              default: _e(() => [
                we(m(p(a)("flowEditor.debug.confirmReplaceConfirm")), 1)
              ]),
              _: 1
            })
          ]),
          default: _e(() => [
            Je.value ? (i(), u("p", xf, m(p(a)("flowEditor.debug.confirmReplace", {
              lineFrom: Je.value.fromLine,
              lineTo: Je.value.toLine
            })), 1)) : T("", !0)
          ]),
          _: 1
        }, 8, ["model-value", "title"]),
        (i(), Ut(yn, { to: "body" }, [
          S.value ? (i(), u("div", Uf, [
            t("div", Bf, [
              ve(rc, { onClosed: b })
            ])
          ])) : T("", !0)
        ])),
        ve(pc, {
          open: p(Ke).prereqMissing !== null,
          "package-name": ((te = p(Ke).prereqMissing) == null ? void 0 : te.packageName) ?? "robotcode",
          installing: p(Ke).installing,
          "install-error": p(Ke).installError,
          onInstall: d,
          onCancel: v
        }, null, 8, ["open", "package-name", "installing", "install-error"])
      ]);
    };
  }
}), If = /* @__PURE__ */ qe(Of, [["__scopeId", "data-v-b19bd9b2"]]), Wf = { class: "read-only-banner__msg" }, jf = ["href"], qf = /* @__PURE__ */ je({
  __name: "ReadOnlyBanner",
  props: {
    role: {},
    adminContactEmail: {}
  },
  setup(e) {
    const n = e, { t: r } = mt(), c = O(() => n.role === "viewer" || n.role === "runner"), g = O(() => n.role === "viewer" ? r("editor.readOnly.viewer") : n.role === "runner" ? r("editor.readOnly.runner") : ""), a = O(
      () => n.adminContactEmail ? `mailto:${n.adminContactEmail}?subject=${encodeURIComponent("Access request")}` : null
    );
    return (_, y) => c.value ? (i(), u("div", {
      key: 0,
      class: ne(["read-only-banner", `read-only-banner--${e.role}`]),
      role: "status",
      "aria-live": "polite"
    }, [
      t("span", Wf, m(g.value), 1),
      a.value ? (i(), u("a", {
        key: 0,
        href: a.value,
        class: "read-only-banner__cta"
      }, m(p(r)("editor.readOnly.contactAdmin")), 9, jf)) : T("", !0)
    ], 2)) : T("", !0);
  }
}), zf = /* @__PURE__ */ qe(qf, [["__scopeId", "data-v-078dbe3d"]]), Gf = /* @__PURE__ */ new Set([
  // BuiltIn library
  "call method",
  "catenate",
  "comment",
  "continue for loop",
  "continue for loop if",
  "convert to binary",
  "convert to boolean",
  "convert to bytes",
  "convert to hex",
  "convert to integer",
  "convert to number",
  "convert to octal",
  "convert to string",
  "create dictionary",
  "create list",
  "evaluate",
  "exit for loop",
  "exit for loop if",
  "fail",
  "fatal error",
  "get count",
  "get length",
  "get library instance",
  "get time",
  "get variable value",
  "get variables",
  "import library",
  "import resource",
  "import variables",
  "keyword should exist",
  "length should be",
  "log",
  "log many",
  "log to console",
  "log variables",
  "no operation",
  "pass execution",
  "pass execution if",
  "regexp escape",
  "reload library",
  "remove tags",
  "repeat keyword",
  "replace variables",
  "return from keyword",
  "return from keyword if",
  "run keyword",
  "run keyword and continue on failure",
  "run keyword and expect error",
  "run keyword and ignore error",
  "run keyword and return",
  "run keyword and return if",
  "run keyword and return status",
  "run keyword if",
  "run keyword if all critical tests passed",
  "run keyword if all tests passed",
  "run keyword if any critical tests failed",
  "run keyword if any tests failed",
  "run keyword if test failed",
  "run keyword if test passed",
  "run keyword if timeout occurred",
  "run keyword unless",
  "run keywords",
  "set global variable",
  "set library search order",
  "set log level",
  "set suite documentation",
  "set suite metadata",
  "set suite variable",
  "set tags",
  "set test documentation",
  "set test message",
  "set test variable",
  "set variable",
  "set variable if",
  "should be empty",
  "should be equal",
  "should be equal as integers",
  "should be equal as numbers",
  "should be equal as strings",
  "should be true",
  "should contain",
  "should contain x times",
  "should end with",
  "should match",
  "should match regexp",
  "should not be empty",
  "should not be equal",
  "should not be equal as integers",
  "should not be equal as numbers",
  "should not be equal as strings",
  "should not be true",
  "should not contain",
  "should not end with",
  "should not match",
  "should not match regexp",
  "should not start with",
  "should start with",
  "sleep",
  "variable should exist",
  "variable should not exist",
  "wait until keyword succeeds",
  "skip",
  "skip if",
  // String library
  "convert to lowercase",
  "convert to uppercase",
  "decode bytes to string",
  "encode string to bytes",
  "fetch from left",
  "fetch from right",
  "generate random string",
  "get line",
  "get line count",
  "get lines containing string",
  "get lines matching pattern",
  "get lines matching regexp",
  "get regexp matches",
  "get substring",
  "remove string",
  "remove string using regexp",
  "replace string",
  "replace string using regexp",
  "should be byte string",
  "should be lowercase",
  "should be string",
  "should be titlecase",
  "should be unicode string",
  "should be uppercase",
  "should not be string",
  "split string",
  "split string from right",
  "split string to characters",
  "split to lines",
  // Collections library
  "append to list",
  "combine lists",
  "convert to dictionary",
  "convert to list",
  "copy dictionary",
  "copy list",
  "count values in list",
  "dictionaries should be equal",
  "dictionary should contain item",
  "dictionary should contain key",
  "dictionary should contain sub dictionary",
  "dictionary should contain value",
  "dictionary should not contain key",
  "dictionary should not contain value",
  "get dictionary items",
  "get dictionary keys",
  "get dictionary values",
  "get from dictionary",
  "get from list",
  "get index from list",
  "get match count",
  "get matches",
  "get slice from list",
  "insert into list",
  "keep in dictionary",
  "list should contain sub list",
  "list should contain value",
  "list should not contain duplicates",
  "list should not contain value",
  "lists should be equal",
  "log dictionary",
  "log list",
  "pop from dictionary",
  "remove duplicates",
  "remove from dictionary",
  "remove from list",
  "remove values from list",
  "reverse list",
  "set list value",
  "set to dictionary",
  "should contain match",
  "should not contain match",
  "sort list",
  // DateTime library
  "add time to date",
  "add time to time",
  "convert date",
  "convert time",
  "get current date",
  "subtract date from date",
  "subtract time from date",
  "subtract time from time",
  // OperatingSystem library
  "append to environment variable",
  "append to file",
  "copy directory",
  "copy file",
  "copy files",
  "count directories in directory",
  "count files in directory",
  "count items in directory",
  "create binary file",
  "create directory",
  "create file",
  "directory should be empty",
  "directory should exist",
  "directory should not be empty",
  "directory should not exist",
  "empty directory",
  "environment variable should be set",
  "environment variable should not be set",
  "file should be empty",
  "file should exist",
  "file should not be empty",
  "file should not exist",
  "get binary file",
  "get environment variable",
  "get environment variables",
  "get file",
  "get file size",
  "get modified time",
  "grep file",
  "join path",
  "join paths",
  "list directories in directory",
  "list directory",
  "list files in directory",
  "log environment variables",
  "log file",
  "move directory",
  "move file",
  "move files",
  "normalize path",
  "remove directory",
  "remove environment variable",
  "remove file",
  "remove files",
  "run",
  "run and return rc",
  "run and return rc and output",
  "set environment variable",
  "set modified time",
  "should exist",
  "should not exist",
  "split extension",
  "split path",
  "touch",
  "wait until created",
  "wait until removed",
  // Process library
  "get process id",
  "get process object",
  "get process result",
  "is process running",
  "join command line",
  "process should be running",
  "process should be stopped",
  "run process",
  "send signal to process",
  "split command line",
  "start process",
  "stop all processes",
  "stop process",
  "switch process",
  "terminate all processes",
  "terminate process",
  "wait for process",
  // Telnet library
  "close all connections",
  "close connection",
  "execute command",
  "login",
  "open connection",
  "read",
  "read until",
  "read until prompt",
  "read until regexp",
  "set default log level",
  "set encoding",
  "set newline",
  "set prompt",
  "set telnetlib log level",
  "set timeout",
  "switch connection",
  "write",
  "write bare",
  "write control character",
  "write until expected output",
  // XML library
  "add element",
  "clear element",
  "copy element",
  "element attribute should be",
  "element attribute should match",
  "element should exist",
  "element should not exist",
  "element should not have attribute",
  "element text should be",
  "element text should match",
  "element to string",
  "elements should be equal",
  "elements should match",
  "evaluate xpath",
  "get child elements",
  "get element",
  "get element attribute",
  "get element attributes",
  "get element count",
  "get element text",
  "get elements",
  "get elements texts",
  "log element",
  "parse xml",
  "remove element",
  "remove element attribute",
  "remove element attributes",
  "remove elements",
  "remove elements attribute",
  "remove elements attributes",
  "save xml",
  "set element attribute",
  "set element tag",
  "set element text",
  "set elements attribute",
  "set elements tag",
  "set elements text",
  // Screenshot library
  "set screenshot directory",
  "take screenshot",
  "take screenshot without embedding",
  // Dialogs library
  "execute manual step",
  "get selection from user",
  "get value from user",
  "pause execution"
]), Yf = /^(Library|Resource|Variables|Suite Setup|Suite Teardown|Test Setup|Test Teardown|Test Template|Test Timeout|Force Tags|Default Tags|Metadata|Documentation|Test Tags|Keyword Tags|Name)\b/i, Xf = /^\[(Setup|Tags|Teardown|Documentation|Arguments|Return|Template|Timeout)\]/i, Zf = /^\b(FOR|END|IF|ELSE IF|ELSE|TRY|EXCEPT|FINALLY|WHILE|BREAK|CONTINUE|RETURN|IN|IN RANGE|IN ENUMERATE|IN ZIP)\b/, Qf = /^(Given|When|Then|And|But)(?=\s)/i, Jf = /^\b(True|False|None|TRUE|FALSE|NONE|EMPTY|SPACE|NULL)\b/, em = /^\\[ntr\\$"'{}@%&xu#]/, $a = /^[$@%&]\{/, tm = /^([A-Za-z_][\w]*)=/;
function om() {
  return bs.define({
    startState() {
      return { section: "", lineHasKeyword: !1, varDepth: 0, expectDotAfterNs: !1 };
    },
    token(e, n) {
      if (n.varDepth > 0)
        return e.match(/^\}/) ? (n.varDepth--, "bracket") : e.match($a) ? (n.varDepth++, "bracket") : (e.match(/^[A-Za-z_][\w ]*/) || e.next(), "variableName");
      if (e.sol()) {
        if (n.lineHasKeyword = n.section === "variables", n.expectDotAfterNs = !1, e.match(/^\*{3}\s*(Settings?|Variables?|Test Cases?|Tasks?|Keywords?|Comments?)\s*\*{0,3}/i)) {
          const r = e.current().toLowerCase();
          return r.includes("setting") ? n.section = "settings" : r.includes("variable") ? n.section = "variables" : r.includes("test") || r.includes("task") ? n.section = "testcases" : r.includes("keyword") ? n.section = "keywords" : n.section = "comments", "heading";
        }
        if (n.section === "comments")
          return e.skipToEnd(), "comment";
        if (e.match(/^\s+\.\.\.(?=\s|$)/))
          return n.lineHasKeyword = !0, "meta";
        if ((n.section === "testcases" || n.section === "keywords") && !e.match(/^\s/, !1))
          return e.match(/^[^\s][^ ]*( [^ ]+)*/), "definition";
      }
      if (n.expectDotAfterNs && (n.expectDotAfterNs = !1, e.match(/^\./)))
        return "punctuation";
      if (e.match(/^#.*/)) return "comment";
      if (e.match(/^  +/)) return "punctuation";
      if (e.match($a))
        return n.varDepth = 1, "bracket";
      if (e.match(/^\[/) || e.match(/^\]/)) return "bracket";
      if (e.match(Xf) || n.section === "settings" && e.match(Yf))
        return n.lineHasKeyword = !0, "meta";
      if (e.match(Zf))
        return n.lineHasKeyword = !0, "keyword";
      if (!n.lineHasKeyword && e.match(Qf)) return "keyword";
      if (e.match(/^\.\.\.(?=\s|$)/)) return "meta";
      if (e.match(/^[A-Za-z_]/, !1)) {
        if (!n.lineHasKeyword) {
          const r = e.string.slice(e.pos), c = r.match(/^([A-Z][\w]*)(?=\.[A-Z])/);
          if (c)
            return e.pos += c[1].length, n.expectDotAfterNs = !0, "tagName";
          const g = r.match(/^[A-Za-z_][\w]*(?: [A-Za-z_][\w]*)*/);
          if (g) {
            const a = g[0], _ = a.split(" ");
            for (let y = _.length; y >= 1; y--) {
              const k = _.slice(0, y).join(" ");
              if (Gf.has(k.toLowerCase()))
                return e.pos += k.length, n.lineHasKeyword = !0, "function";
            }
            return e.pos += a.length, n.lineHasKeyword = !0, "function";
          }
          return e.next(), null;
        }
        return e.match(tm) ? (e.pos -= 1, "attributeName") : e.match(Jf) ? "atom" : (e.match(/^[A-Za-z_][\w]*/), null);
      }
      return e.match(em) ? "string-2" : e.match(/^==/) || e.match(/^=/) ? "operator" : e.match(/^"(?:[^"\\]|\\.)*"/) || e.match(/^'(?:[^'\\]|\\.)*'/) ? "string" : e.match(/^-?\d+(\.\d+)?\b/) ? "number" : (e.eatSpace() || e.next(), null);
    }
  });
}
const nm = ys.define([
  // Section headers (*** Settings ***) — strong navy heading.
  { tag: Re.heading, color: "#1A2D50", fontWeight: "700" },
  // Comments — muted grey, italic.
  { tag: [Re.comment, Re.lineComment, Re.blockComment], color: "#6B7A99", fontStyle: "italic" },
  // Test case / keyword DEFINITION names — brand-blue, bold (loud).
  { tag: Re.definition(Re.name), color: "#1A2D50", fontWeight: "700" },
  // Keyword CALLS (the verbs in test bodies) — brand-blue, regular.
  { tag: [Re.function(Re.name), Re.function(Re.variableName)], color: "#2D63B0" },
  // Library namespace (`Browser` in `Browser.Click`).
  { tag: Re.tagName, color: "#7B61FF" },
  // Control-flow words (FOR / IF / END / WHILE) and BDD prefixes —
  // accent orange so they read as structural, not as keywords-of-RF.
  { tag: [Re.keyword, Re.controlKeyword], color: "#D4883E", fontWeight: "600" },
  // Setting / [Tag] / continuation `...` — muted teal-ish.
  { tag: Re.meta, color: "#2C9846", fontWeight: "500" },
  // Variable braces `${ }` and index brackets `[ ]`.
  { tag: Re.bracket, color: "#7B61FF", fontWeight: "500" },
  // Variable names inside `${name}`.
  { tag: Re.variableName, color: "#1A2D50" },
  // Strings (quoted).
  { tag: Re.string, color: "#2C9846" },
  // Escape sequences inside arguments (`\n`, `\t`, `\\`).
  { tag: Re.special(Re.string), color: "#D4883E", fontWeight: "600" },
  // Numbers.
  { tag: Re.number, color: "#C0392B" },
  // Atoms (True / False / None / EMPTY / SPACE).
  { tag: Re.atom, color: "#C0392B", fontWeight: "600" },
  // Named-argument names (`force` in `force=True`).
  { tag: Re.attributeName, color: "#7B61FF", fontStyle: "italic" },
  // Operators (`=`, `==`).
  { tag: Re.operator, color: "#D4883E" },
  // Cell separators (2+ spaces) and dots — keep them faint.
  { tag: Re.punctuation, color: "#9AA5BF" }
]);
function ns(e) {
  return e.replace(/\.robot$/i, ".rbs.json");
}
async function am(e, n) {
  const r = ns(n);
  try {
    const c = await xa(e, r), g = JSON.parse(c.content);
    return Rs(g), Array.isArray(g.commands) ? g : null;
  } catch {
    return null;
  }
}
async function sm(e, n, r) {
  const c = ns(n), g = JSON.stringify(r, null, 2) + `
`;
  await Ua(e, c, g);
}
const Ta = [
  "keyword",
  "assignment",
  "var",
  "comment",
  "for",
  "if",
  "else_if",
  "else",
  "while",
  "try",
  "except",
  "finally",
  "end",
  "break",
  "continue",
  "return"
], Ea = ["LOCAL", "TEST", "TASK", "SUITE", "GLOBAL"], ea = ["IN", "IN RANGE", "IN ENUMERATE", "IN ZIP"];
function Bo(e = "keyword") {
  return {
    type: e,
    keyword: "",
    args: [],
    returnVars: [],
    condition: "",
    loopVar: "${item}",
    loopFlavor: "IN",
    loopValues: [],
    exceptPattern: "",
    exceptVar: "",
    varScope: "",
    comment: ""
  };
}
function Cn(e) {
  return e.startsWith("\\#") ? e.slice(1) : e;
}
function as(e) {
  return e && (e.startsWith("#") ? "\\" + e : e);
}
function lm(e) {
  return e && as(e).replace(/ {2,}/g, (r) => "\\ ".repeat(r.length));
}
function Sa(e) {
  return e && Cn(e.replace(/\\ /g, " "));
}
const ss = /^# rbs:([a-f0-9]{8,32})$/, de = "    ", rm = /* @__PURE__ */ new Set([
  "FOR",
  "END",
  "IF",
  "ELSE",
  "ELSE IF",
  "WHILE",
  "TRY",
  "EXCEPT",
  "FINALLY",
  "BREAK",
  "CONTINUE",
  "RETURN",
  "VAR"
]);
function im(e) {
  const n = e.split(/  +|\t+/)[0];
  return rm.has(n);
}
function um(e, n) {
  for (let r = n; r < e.length; r++) {
    const c = e[r], g = c.trimEnd();
    if (g) {
      if (ls.test(g) || !/^\s/.test(c)) return !1;
      if (/^\s*\[Template\]\s+\S/i.test(c)) return !0;
    }
  }
  return !1;
}
function cm(e) {
  return e.startsWith("#") && !ss.test(e);
}
function dm(e) {
  const n = Bo(), r = e.trim();
  if (!r) return n;
  if (r.startsWith("#"))
    return n.type = "comment", n.comment = r, n;
  let c = r.split(/  +|\t+/).filter((E) => E !== "");
  if (c.length === 0) return n;
  const g = c[c.length - 1], a = ss.exec(g);
  if (a && (n.rbs_id = a[1], c.pop(), c.length === 0))
    return n;
  const _ = c.findIndex(cm);
  if (_ >= 0 && (n.trailingComment = c.slice(_).join(" "), c = c.slice(0, _), c.length === 0))
    return n.type = "comment", n.comment = n.trailingComment, n.trailingComment = void 0, n;
  const y = c[0];
  if (y === "FOR") {
    n.type = "for";
    let E = -1;
    for (let B = 1; B < c.length; B++)
      if (ea.includes(c[B])) {
        E = B;
        break;
      }
    if (E > 0) {
      const B = c.slice(1, E);
      let I = c[E], j = E + 1;
      const ue = c[j];
      I === "IN" && ue && ["ENUMERATE", "RANGE", "ZIP"].includes(ue) && (I = `IN ${ue}`, j += 1), n.loopVars = B, n.loopVar = B[0] || "${item}", n.loopFlavor = I, n.loopValues = c.slice(j);
    } else
      n.loopVar = c[1] || "${item}", n.loopFlavor = "IN", n.loopValues = c.slice(3);
    return n;
  }
  if (y === "END")
    return n.type = "end", n;
  if (y === "IF")
    return n.type = "if", n.condition = c.slice(1).join("    "), n;
  if (y === "ELSE IF")
    return n.type = "else_if", n.condition = c.slice(1).join("    "), n;
  if (y === "ELSE")
    return n.type = "else", n;
  if (y === "WHILE")
    return n.type = "while", n.condition = c.slice(1).join("    "), n;
  if (y === "TRY")
    return n.type = "try", n;
  if (y === "EXCEPT") {
    n.type = "except";
    const E = c.indexOf("AS");
    return E >= 1 ? (n.exceptPattern = c.slice(1, E).join("    "), n.exceptVar = c[E + 1] || "") : n.exceptPattern = c.slice(1).join("    "), n;
  }
  if (y === "FINALLY")
    return n.type = "finally", n;
  if (y === "BREAK")
    return n.type = "break", n;
  if (y === "CONTINUE")
    return n.type = "continue", n;
  if (y === "RETURN")
    return n.type = "return", n.args = c.slice(1), n;
  if (y === "VAR") {
    n.type = "var", n.returnVars = c[1] ? [c[1]] : ["${var}"];
    const E = c.slice(2), B = E.findIndex((I) => /^scope=/i.test(I));
    return B >= 0 ? (n.varScope = E[B].replace(/^scope=/i, ""), n.args = E.slice(0, B)) : n.args = E, n;
  }
  const k = /^[$@&%]\{[^}]+\}\s*=?$/, A = [];
  let V = 0;
  for (let E = 0; E < c.length; E++) {
    const B = c[E].trim();
    if (k.test(B)) {
      const I = B.replace(/\s*=$/, "");
      if (A.push(I), B.endsWith("=")) {
        V = E + 1;
        break;
      }
      if (E + 1 < c.length && c[E + 1].trim() === "=") {
        V = E + 2;
        break;
      }
    } else if (B === "=" && A.length > 0) {
      V = E + 1;
      break;
    } else {
      V = E;
      break;
    }
  }
  return A.length > 0 && V < c.length ? (n.type = "assignment", n.returnVars = A, n.keyword = c[V], n.args = c.slice(V + 1).map(Cn), n) : (n.type = "keyword", n.keyword = c[0], n.args = c.slice(1).map(Cn), n);
}
function pm(e) {
  const n = (g) => {
    let a = g;
    return e.rbs_id && (a += `${de}# rbs:${e.rbs_id}`), e.trailingComment && (a += `${de}${e.trailingComment}`), a;
  }, r = e.args.map(as);
  let c;
  switch (e.type) {
    case "keyword":
      c = [e.keyword, ...r].filter(Boolean).join(de);
      break;
    case "assignment": {
      c = [...e.returnVars.map(
        (a, _) => _ === e.returnVars.length - 1 ? a + "=" : a
      ), e.keyword, ...r].filter(Boolean).join(de);
      break;
    }
    case "for": {
      c = ["FOR", ...e.loopVars && e.loopVars.length > 0 ? e.loopVars : [e.loopVar], e.loopFlavor, ...e.loopValues].filter(Boolean).join(de);
      break;
    }
    case "end":
      c = "END";
      break;
    case "if":
      c = ["IF", e.condition].filter(Boolean).join(de);
      break;
    case "else_if":
      c = ["ELSE IF", e.condition].filter(Boolean).join(de);
      break;
    case "else":
      c = "ELSE";
      break;
    case "while":
      c = ["WHILE", e.condition].filter(Boolean).join(de);
      break;
    case "try":
      c = "TRY";
      break;
    case "except": {
      const g = ["EXCEPT"];
      e.exceptPattern && g.push(e.exceptPattern), e.exceptVar && g.push("AS", e.exceptVar), c = g.join(de);
      break;
    }
    case "finally":
      c = "FINALLY";
      break;
    case "break":
      c = "BREAK";
      break;
    case "continue":
      c = "CONTINUE";
      break;
    case "return":
      c = ["RETURN", ...e.args].filter(Boolean).join(de);
      break;
    case "var": {
      const g = ["VAR", e.returnVars[0] || "${var}", ...e.args];
      e.varScope && g.push("scope=" + e.varScope), c = g.filter(Boolean).join(de);
      break;
    }
    case "comment":
      return e.comment || "# ";
    default:
      return "";
  }
  return n(c);
}
function fm(e) {
  return /(^|[\\/])__init__\.robot$/i.test(e ?? "");
}
function mm(e) {
  return e.split(`
`).some((n) => /^﻿?\*+[ ]*(Test Cases?|Tasks?)[ ]*\**/i.test(n.trim()));
}
const ls = /^﻿?\*+[ ]*(Settings?|Variables?|Test Cases?|Tasks?|Keywords?)[ ]*\**/i;
function gm(e) {
  var V, E;
  const n = e.split(`
`), r = {
    settings: [],
    variables: [],
    testCases: [],
    keywords: [],
    preambleLines: []
  };
  let c = "none", g = null, a = null, _ = [], y = !1, k = null;
  const A = () => {
    g && a && (a === "testcase" ? r.testCases.push(g) : r.keywords.push(g)), g = null, a = null, k = null;
  };
  for (let B = 0; B < n.length; B++) {
    const I = n[B], j = I.trimEnd(), ue = j.match(ls);
    if (ue) {
      A();
      const M = ue[1].toLowerCase();
      M.startsWith("setting") ? c = "settings" : M.startsWith("variable") ? c = "variables" : M.startsWith("test") || M.startsWith("task") ? c = "testcases" : M.startsWith("keyword") && (c = "keywords");
      continue;
    }
    if (c === "none") {
      r.preambleLines.push(j);
      continue;
    }
    if (c === "settings") {
      if (!j || j.startsWith("#")) {
        j.startsWith("#") && r.settings.push({ key: "#", value: j, args: [] });
        continue;
      }
      if (j.startsWith("...")) {
        const J = j.slice(3).trim(), ae = r.settings[r.settings.length - 1];
        ae && ae.key !== "#" && (ae.value = ae.value ? `${ae.value}
${J}` : J);
        continue;
      }
      const M = j.split(/  +|\t+/).filter((J) => J !== "");
      M.length >= 1 && r.settings.push({ key: M[0], value: M[1] || "", args: M.slice(2) });
      continue;
    }
    if (c === "variables") {
      if (!j || j.startsWith("#")) {
        j.startsWith("#") && r.variables.push({ name: "#", value: j });
        continue;
      }
      if (j.startsWith("...")) {
        const J = j.slice(3).trim(), ae = r.variables[r.variables.length - 1];
        ae && ae.name !== "#" && (ae.value = ae.value ? `${ae.value}${de}${J}` : J);
        continue;
      }
      const M = j.split(/  +|\t+/).filter((J) => J !== "");
      M.length >= 1 && r.variables.push({ name: M[0], value: M.slice(1).join(de) });
      continue;
    }
    if (c === "testcases" || c === "keywords") {
      if (!j) continue;
      const M = /^\s/.test(I) || I.startsWith("	");
      if (!M && j.startsWith("#")) {
        _.push(j);
        continue;
      }
      if (!M && j) {
        A(), y = !1, c === "testcases" ? (g = {
          name: j,
          documentation: "",
          tags: [],
          setup: "",
          teardown: "",
          timeout: "",
          template: "",
          steps: []
        }, a = "testcase", y = um(n, B + 1)) : (g = {
          name: j,
          documentation: "",
          arguments: [],
          tags: [],
          setup: "",
          teardown: "",
          timeout: "",
          returnValue: "",
          steps: []
        }, a = "keyword"), _.length && (g.leadingComments = _, _ = []);
        continue;
      }
      if (g && M) {
        const J = j.trim(), ae = a === "testcase" ? g : null, z = !!ae && (!!ae.template || y);
        if (J.startsWith("...")) {
          const Y = J.slice(3).trim().split(/  +|\t+/).filter((U) => U !== "");
          if (z && ae.templateRows && ae.templateRows.length > 0 && g.steps.length === 0) {
            ae.templateRows[ae.templateRows.length - 1].push(...Y.map(Sa));
            continue;
          }
          if (k) {
            const U = Y.join("    ");
            if (k === "documentation") {
              const Z = Y.join(" ");
              g.documentation = g.documentation ? g.documentation + `
` + Z : Z;
            } else if (k === "tags")
              g.tags.push(...Y);
            else if (k === "arguments")
              "arguments" in g && g.arguments.push(...Y);
            else if (k === "return") {
              if ("returnValue" in g) {
                const Z = g;
                Z.returnValue = Z.returnValue ? Z.returnValue + de + U : U;
              }
            } else {
              const Z = k === "setup" ? "setup" : k === "teardown" ? "teardown" : k === "timeout" ? "timeout" : k === "template" ? "template" : "";
              if (Z === "template") {
                if ("template" in g) {
                  const ce = g;
                  ce.template = ce.template ? ce.template + de + U : U;
                }
              } else if (Z) {
                const ce = g;
                ce[Z] = ce[Z] ? ce[Z] + de + U : U;
              }
            }
            continue;
          }
          if (g.steps.length > 0) {
            const U = g.steps[g.steps.length - 1];
            switch (U.type) {
              case "keyword":
              case "assignment":
              case "return":
                U.args.push(...Y.map(Cn));
                break;
              case "for":
                U.loopValues.push(...Y);
                break;
              default:
                U.condition !== void 0 && Y.length && (U.condition += de + Y.join(de));
            }
          } else g.documentation && (g.documentation += `
` + Y.join(" "));
          continue;
        }
        const D = J.match(/^\[(Documentation|Tags|Setup|Teardown|Timeout|Template|Arguments|Return)\]\s*(.*)/i);
        if (D) {
          const Y = D[1].toLowerCase(), U = ((V = D[2]) == null ? void 0 : V.trim()) || "";
          switch (Y) {
            case "documentation":
              g.documentation = U;
              break;
            case "tags":
              g.tags = U ? U.split(/  +|\t+/).map((Z) => Z.trim()).filter(Boolean) : [];
              break;
            case "setup":
              g.setup = U;
              break;
            case "teardown":
              g.teardown = U;
              break;
            case "timeout":
              g.timeout = U;
              break;
            case "template":
              "template" in g && (g.template = U);
              break;
            case "arguments":
              "arguments" in g && (g.arguments = U ? U.split(/  +|\t+/).filter(Boolean) : []);
              break;
            case "return":
              "returnValue" in g && (g.returnValue = U);
              break;
          }
          k = Y;
          continue;
        }
        if (z && !im(J)) {
          const Y = J.split(/  +|\t+/).filter((U) => U !== "").map(Sa);
          ae.templateRows || (ae.templateRows = []), ae.templateRows.push(Y), k = null;
          continue;
        }
        const R = dm(J);
        R._lineNumber = B + 1;
        const H = (((E = I.match(/^[ \t]+/)) == null ? void 0 : E[0]) ?? "").replace(/\t/g, "    ");
        R.depth = Math.max(0, Math.floor(H.length / 4) - 1), g.steps.push(R), k = null;
      }
      continue;
    }
  }
  return A(), _.length && (r.trailingComments = _), r;
}
function La(e, n) {
  const r = n.split(`
`), c = [];
  c.push(de + e + de + r[0]);
  for (let g = 1; g < r.length; g++)
    c.push(de + "..." + de + r[g]);
  return c;
}
function vm(e, n = {}) {
  const r = n.isResource ?? !1, c = [];
  for (const y of e.preambleLines) c.push(y);
  if (e.settings.length > 0) {
    c.length > 0 && c[c.length - 1] !== "" && c.push(""), c.push("*** Settings ***");
    for (const y of e.settings) {
      if (y.key === "#") {
        c.push(y.value);
        continue;
      }
      if (y.value && y.value.includes(`
`)) {
        const A = y.value.split(`
`);
        let V = y.key + de + A[0];
        for (const E of y.args) V += de + E;
        c.push(V);
        for (let E = 1; E < A.length; E++) c.push("..." + de + A[E]);
        continue;
      }
      let k = y.key;
      y.value && (k += de + y.value);
      for (const A of y.args) k += de + A;
      c.push(k);
    }
  }
  if (e.variables.length > 0) {
    c.length > 0 && c[c.length - 1] !== "" && c.push(""), c.push("*** Variables ***");
    for (const y of e.variables) {
      if (y.name === "#") {
        c.push(y.value);
        continue;
      }
      c.push(y.value ? y.name + de + y.value : y.name);
    }
  }
  const g = (y) => {
    if (y.leadingComments) for (const k of y.leadingComments) c.push(k);
  }, a = (y) => {
    const k = de.repeat((y.depth ?? 0) + 1);
    c.push(k + pm(y));
  };
  if (!r && e.testCases.length > 0) {
    c.length > 0 && c[c.length - 1] !== "" && c.push(""), c.push("*** Test Cases ***");
    for (let y = 0; y < e.testCases.length; y++) {
      const k = e.testCases[y];
      g(k);
      const A = k.name.trim() || `Test Case ${y + 1}`;
      if (c.push(A), k.documentation && c.push(...La("[Documentation]", k.documentation)), k.tags.length > 0 && c.push(de + "[Tags]" + de + k.tags.join(de)), k.setup && c.push(de + "[Setup]" + de + k.setup), k.teardown && c.push(de + "[Teardown]" + de + k.teardown), k.timeout && c.push(de + "[Timeout]" + de + k.timeout), k.template && c.push(de + "[Template]" + de + k.template), k.templateRows)
        for (const V of k.templateRows)
          c.push(de + V.map(lm).join(de));
      for (const V of k.steps) a(V);
      c.push("");
    }
  }
  if (e.keywords.length > 0) {
    c.length > 0 && c[c.length - 1] !== "" && c.push(""), c.push("*** Keywords ***");
    for (let y = 0; y < e.keywords.length; y++) {
      const k = e.keywords[y];
      g(k);
      const A = k.name.trim() || `Keyword ${y + 1}`;
      c.push(A), k.arguments.length && c.push(de + "[Arguments]" + de + k.arguments.join(de)), k.documentation && c.push(...La("[Documentation]", k.documentation)), k.tags.length > 0 && c.push(de + "[Tags]" + de + k.tags.join(de)), k.setup && c.push(de + "[Setup]" + de + k.setup), k.teardown && c.push(de + "[Teardown]" + de + k.teardown), k.timeout && c.push(de + "[Timeout]" + de + k.timeout), k.returnValue && c.push(de + "[Return]" + de + k.returnValue);
      for (const V of k.steps) a(V);
      c.push("");
    }
  }
  if (e.trailingComments) for (const y of e.trailingComments) c.push(y);
  let _ = c.join(`
`);
  return _ = _.replace(/\n{3,}/g, `

`), _.endsWith(`
`) || (_ += `
`), _;
}
function hm(e) {
  const n = /* @__PURE__ */ new Set(), r = /\s+# rbs:([a-f0-9]{8,32})\s*$/gm;
  let c;
  for (; (c = r.exec(e)) !== null; ) n.add(c[1]);
  return n;
}
const ym = { class: "spec-tabs" }, bm = { class: "tab-buttons" }, wm = { class: "tab-toolbar" }, _m = { class: "badge badge-robot" }, km = {
  key: 0,
  class: "badge badge-info"
}, Cm = {
  key: 1,
  class: "badge badge-info"
}, $m = ["title"], Tm = ["title"], Em = ["title"], Sm = ["title"], Lm = {
  key: 1,
  class: "parse-error-banner"
}, Vm = { class: "editor-section section-settings" }, Am = { class: "collapse-icon" }, Nm = { class: "section-count" }, Rm = { class: "section-body" }, Fm = {
  key: 0,
  class: "empty-hint"
}, Pm = ["onUpdate:modelValue"], Dm = ["onClick", "title"], Mm = ["onUpdate:modelValue"], xm = ["value"], Um = {
  key: 0,
  class: "keyword-autocomplete-wrapper flex-1"
}, Bm = ["onUpdate:modelValue", "placeholder", "onFocus", "onKeydown", "onInput"], Km = ["onMousedown"], Hm = { class: "kw-suggestion-name" }, Om = {
  key: 1,
  class: "form-hl-wrap flex-1"
}, Im = ["innerHTML"], Wm = ["onUpdate:modelValue", "placeholder"], jm = {
  key: 2,
  class: "setting-args"
}, qm = ["onUpdate:modelValue", "placeholder"], zm = ["onClick"], Gm = ["onClick"], Ym = ["onClick", "title"], Xm = { class: "editor-section section-variables" }, Zm = { class: "collapse-icon" }, Qm = { class: "section-count" }, Jm = { class: "section-body" }, eg = {
  key: 0,
  class: "empty-hint"
}, tg = ["onUpdate:modelValue"], og = ["onClick", "title"], ng = { class: "form-hl-wrap hl-var-name" }, ag = ["innerHTML"], sg = ["onUpdate:modelValue", "placeholder", "onBlur"], lg = { class: "form-hl-wrap flex-1" }, rg = ["innerHTML"], ig = ["onUpdate:modelValue", "placeholder"], ug = ["onClick", "title"], cg = {
  key: 0,
  class: "editor-section section-testcases"
}, dg = { class: "collapse-icon" }, pg = { class: "section-count" }, fg = { class: "section-body" }, mg = {
  key: 0,
  class: "empty-hint"
}, gg = ["onClick"], vg = { class: "collapse-icon" }, hg = { class: "item-title" }, yg = { class: "item-count" }, bg = ["onClick", "title"], wg = { class: "item-body" }, _g = { class: "form-group" }, kg = { class: "form-label" }, Cg = ["onUpdate:modelValue", "placeholder"], $g = { class: "form-group" }, Tg = { class: "form-label" }, Eg = ["onUpdate:modelValue", "placeholder"], Sg = {
  key: 0,
  class: "form-group"
}, Lg = { class: "form-label" }, Vg = ["onClick"], Ag = { class: "chips-container" }, Ng = ["onClick"], Rg = { class: "chip-input-wrapper" }, Fg = ["value", "onInput", "placeholder", "onKeydown"], Pg = ["onClick"], Dg = { class: "meta-toggles" }, Mg = ["onClick"], xg = ["onClick"], Ug = ["onClick"], Bg = ["onClick"], Kg = ["onClick"], Hg = {
  key: 1,
  class: "form-group"
}, Og = { class: "form-label" }, Ig = ["onClick"], Wg = { class: "setup-teardown-row" }, jg = { class: "keyword-autocomplete-wrapper" }, qg = ["value", "onInput", "placeholder", "onFocus", "onKeydown"], zg = ["onMousedown"], Gg = { class: "kw-suggestion-name" }, Yg = {
  key: 0,
  class: "kw-suggestion-args"
}, Xg = {
  key: 0,
  class: "step-args-group"
}, Zg = {
  key: 0,
  class: "arg-label"
}, Qg = ["innerHTML"], Jg = ["value", "onInput", "placeholder"], ev = ["onClick"], tv = ["onClick", "title"], ov = {
  key: 2,
  class: "form-group"
}, nv = { class: "form-label" }, av = ["onClick"], sv = { class: "setup-teardown-row" }, lv = { class: "keyword-autocomplete-wrapper" }, rv = ["value", "onInput", "placeholder", "onFocus", "onKeydown"], iv = ["onMousedown"], uv = { class: "kw-suggestion-name" }, cv = {
  key: 0,
  class: "kw-suggestion-args"
}, dv = {
  key: 0,
  class: "step-args-group"
}, pv = {
  key: 0,
  class: "arg-label"
}, fv = ["innerHTML"], mv = ["value", "onInput", "placeholder"], gv = ["onClick"], vv = ["onClick", "title"], hv = {
  key: 3,
  class: "form-row"
}, yv = {
  key: 0,
  class: "form-group flex-1"
}, bv = { class: "form-label" }, wv = ["onClick"], _v = { class: "form-hl-wrap" }, kv = ["innerHTML"], Cv = ["onUpdate:modelValue", "placeholder"], $v = {
  key: 1,
  class: "form-group flex-1"
}, Tv = { class: "form-label" }, Ev = ["onClick"], Sv = { class: "form-hl-wrap" }, Lv = ["innerHTML"], Vv = ["onUpdate:modelValue", "placeholder"], Av = { class: "form-group" }, Nv = { class: "form-label" }, Rv = { class: "steps-list" }, Fv = { class: "step-number" }, Pv = ["onUpdate:modelValue", "onChange"], Dv = ["value"], Mv = { class: "keyword-autocomplete-wrapper" }, xv = ["onUpdate:modelValue", "placeholder", "onFocus", "onKeydown", "onInput"], Uv = ["onMousedown"], Bv = { class: "kw-suggestion-name" }, Kv = {
  key: 0,
  class: "kw-suggestion-args"
}, Hv = {
  key: 0,
  class: "step-args-group"
}, Ov = {
  key: 0,
  class: "arg-label"
}, Iv = ["innerHTML"], Wv = ["onUpdate:modelValue", "placeholder", "onInput", "onKeydown"], jv = {
  key: 1,
  class: "arg-ac-dropdown"
}, qv = ["onMousedown"], zv = ["onClick"], Gv = ["onClick", "title"], Yv = ["onUpdate:modelValue", "onBlur"], Xv = ["onClick"], Zv = ["onClick"], Qv = { class: "keyword-autocomplete-wrapper" }, Jv = ["onUpdate:modelValue", "placeholder", "onFocus", "onKeydown", "onInput"], eh = ["onMousedown"], th = { class: "kw-suggestion-name" }, oh = {
  key: 0,
  class: "kw-suggestion-args"
}, nh = {
  key: 0,
  class: "step-args-group"
}, ah = {
  key: 0,
  class: "arg-label"
}, sh = ["innerHTML"], lh = ["onUpdate:modelValue", "placeholder", "onInput", "onKeydown"], rh = {
  key: 1,
  class: "arg-ac-dropdown"
}, ih = ["onMousedown"], uh = ["onClick"], ch = ["onClick", "title"], dh = { class: "step-var-chip" }, ph = ["onUpdate:modelValue", "onBlur"], fh = ["innerHTML"], mh = ["onUpdate:modelValue", "placeholder"], gh = ["onClick"], vh = ["onClick", "title"], hh = ["onUpdate:modelValue"], yh = ["value"], bh = { class: "form-hl-wrap hl-var-inline" }, wh = ["innerHTML"], _h = ["onUpdate:modelValue"], kh = ["onUpdate:modelValue"], Ch = ["value"], $h = ["onUpdate:modelValue", "placeholder"], Th = ["onClick"], Eh = ["onClick", "title"], Sh = {
  key: 4,
  class: "form-hl-wrap flex-1 hl-condition-wrap"
}, Lh = ["innerHTML"], Vh = ["onUpdate:modelValue", "placeholder"], Ah = ["onUpdate:modelValue", "placeholder"], Nh = {
  key: 0,
  class: "step-as-label"
}, Rh = {
  key: 1,
  class: "form-hl-wrap hl-var-inline"
}, Fh = ["innerHTML"], Ph = ["onUpdate:modelValue"], Dh = ["onUpdate:modelValue", "placeholder"], Mh = ["onClick"], xh = ["onClick", "title"], Uh = ["onUpdate:modelValue"], Bh = { class: "step-actions" }, Kh = ["onClick", "disabled"], Hh = ["onClick", "disabled"], Oh = ["onClick", "title"], Ih = { class: "add-step-bar" }, Wh = ["onClick"], jh = ["onClick"], qh = ["onClick"], zh = ["onClick"], Gh = ["onClick"], Yh = ["onClick"], Xh = { class: "editor-section section-keywords" }, Zh = { class: "collapse-icon" }, Qh = { class: "section-count" }, Jh = { class: "section-body" }, ey = {
  key: 0,
  class: "empty-hint"
}, ty = ["onClick"], oy = { class: "collapse-icon" }, ny = { class: "item-title" }, ay = { class: "item-count" }, sy = ["onClick", "title"], ly = { class: "item-body" }, ry = { class: "form-group" }, iy = { class: "form-label" }, uy = ["onUpdate:modelValue", "placeholder"], cy = { class: "form-group" }, dy = { class: "form-label" }, py = { class: "kw-args-list" }, fy = { class: "kw-arg-sep-label" }, my = { class: "kw-arg-type-badge badge-varargs" }, gy = ["value", "onInput", "onBlur"], vy = { class: "kw-arg-type-badge badge-kwargs" }, hy = ["value", "onInput", "onBlur"], yy = {
  key: 0,
  class: "kw-arg-type-badge badge-named"
}, by = {
  key: 1,
  class: "kw-arg-type-badge badge-optional"
}, wy = {
  key: 2,
  class: "kw-arg-type-badge badge-required"
}, _y = ["value", "onInput", "onBlur", "placeholder"], ky = { class: "form-hl-wrap kw-default-wrap" }, Cy = ["innerHTML"], $y = ["value", "onInput", "placeholder"], Ty = ["onClick", "title"], Ey = ["onClick"], Sy = { class: "kw-arg-add-buttons" }, Ly = ["onClick"], Vy = ["onClick"], Ay = ["disabled", "onClick"], Ny = ["disabled", "onClick"], Ry = ["onClick"], Fy = { class: "form-group" }, Py = { class: "form-label" }, Dy = ["onUpdate:modelValue", "placeholder"], My = {
  key: 0,
  class: "form-group"
}, xy = { class: "form-label" }, Uy = ["onClick"], By = { class: "chips-container" }, Ky = ["onClick"], Hy = { class: "chip-input-wrapper" }, Oy = ["value", "onInput", "placeholder", "onKeydown"], Iy = ["onClick"], Wy = { class: "meta-toggles" }, jy = ["onClick"], qy = ["onClick"], zy = ["onClick"], Gy = ["onClick"], Yy = ["onClick"], Xy = {
  key: 1,
  class: "form-group"
}, Zy = { class: "form-label" }, Qy = ["onClick"], Jy = { class: "setup-teardown-row" }, eb = { class: "keyword-autocomplete-wrapper" }, tb = ["value", "onInput", "placeholder", "onFocus", "onKeydown"], ob = ["onMousedown"], nb = { class: "kw-suggestion-name" }, ab = {
  key: 0,
  class: "kw-suggestion-args"
}, sb = {
  key: 0,
  class: "step-args-group"
}, lb = {
  key: 0,
  class: "arg-label"
}, rb = ["innerHTML"], ib = ["value", "onInput", "placeholder"], ub = ["onClick"], cb = ["onClick", "title"], db = {
  key: 2,
  class: "form-group"
}, pb = { class: "form-label" }, fb = ["onClick"], mb = { class: "setup-teardown-row" }, gb = { class: "keyword-autocomplete-wrapper" }, vb = ["value", "onInput", "placeholder", "onFocus", "onKeydown"], hb = ["onMousedown"], yb = { class: "kw-suggestion-name" }, bb = {
  key: 0,
  class: "kw-suggestion-args"
}, wb = {
  key: 0,
  class: "step-args-group"
}, _b = {
  key: 0,
  class: "arg-label"
}, kb = ["innerHTML"], Cb = ["value", "onInput", "placeholder"], $b = ["onClick"], Tb = ["onClick", "title"], Eb = {
  key: 3,
  class: "form-row"
}, Sb = {
  key: 0,
  class: "form-group flex-1"
}, Lb = { class: "form-label" }, Vb = ["onClick"], Ab = { class: "form-hl-wrap" }, Nb = ["innerHTML"], Rb = ["onUpdate:modelValue", "placeholder"], Fb = {
  key: 1,
  class: "form-group flex-1"
}, Pb = { class: "form-label" }, Db = ["onClick"], Mb = { class: "form-hl-wrap" }, xb = ["innerHTML"], Ub = ["onUpdate:modelValue", "placeholder"], Bb = { class: "form-group" }, Kb = { class: "form-label" }, Hb = { class: "steps-list" }, Ob = { class: "step-number" }, Ib = ["onUpdate:modelValue", "onChange"], Wb = ["value"], jb = { class: "keyword-autocomplete-wrapper" }, qb = ["onUpdate:modelValue", "placeholder", "onFocus", "onKeydown", "onInput"], zb = ["onMousedown"], Gb = { class: "kw-suggestion-name" }, Yb = {
  key: 0,
  class: "kw-suggestion-args"
}, Xb = {
  key: 0,
  class: "step-args-group"
}, Zb = {
  key: 0,
  class: "arg-label"
}, Qb = ["innerHTML"], Jb = ["onUpdate:modelValue", "placeholder", "onInput", "onKeydown"], ew = {
  key: 1,
  class: "arg-ac-dropdown"
}, tw = ["onMousedown"], ow = ["onClick"], nw = ["onClick", "title"], aw = ["onUpdate:modelValue", "onBlur"], sw = ["onClick"], lw = ["onClick"], rw = { class: "keyword-autocomplete-wrapper" }, iw = ["onUpdate:modelValue", "placeholder", "onFocus", "onKeydown", "onInput"], uw = ["onMousedown"], cw = { class: "kw-suggestion-name" }, dw = {
  key: 0,
  class: "kw-suggestion-args"
}, pw = {
  key: 0,
  class: "step-args-group"
}, fw = {
  key: 0,
  class: "arg-label"
}, mw = ["innerHTML"], gw = ["onUpdate:modelValue", "placeholder", "onInput", "onKeydown"], vw = {
  key: 1,
  class: "arg-ac-dropdown"
}, hw = ["onMousedown"], yw = ["onClick"], bw = ["onClick", "title"], ww = { class: "step-var-chip" }, _w = ["onUpdate:modelValue", "onBlur"], kw = ["innerHTML"], Cw = ["onUpdate:modelValue", "placeholder"], $w = ["onClick"], Tw = ["onClick", "title"], Ew = ["onUpdate:modelValue"], Sw = ["value"], Lw = { class: "form-hl-wrap hl-var-inline" }, Vw = ["innerHTML"], Aw = ["onUpdate:modelValue"], Nw = ["onUpdate:modelValue"], Rw = ["value"], Fw = ["onUpdate:modelValue", "placeholder"], Pw = ["onClick"], Dw = ["onClick", "title"], Mw = {
  key: 4,
  class: "form-hl-wrap flex-1 hl-condition-wrap"
}, xw = ["innerHTML"], Uw = ["onUpdate:modelValue", "placeholder"], Bw = ["onUpdate:modelValue", "placeholder"], Kw = {
  key: 0,
  class: "step-as-label"
}, Hw = {
  key: 1,
  class: "form-hl-wrap hl-var-inline"
}, Ow = ["innerHTML"], Iw = ["onUpdate:modelValue"], Ww = ["onUpdate:modelValue", "placeholder"], jw = ["onClick"], qw = ["onClick", "title"], zw = ["onUpdate:modelValue"], Gw = { class: "step-actions" }, Yw = ["onClick", "disabled"], Xw = ["onClick", "disabled"], Zw = ["onClick", "title"], Qw = { class: "add-step-bar" }, Jw = ["onClick"], e_ = ["onClick"], t_ = ["onClick"], o_ = ["onClick"], n_ = ["onClick"], a_ = ["onClick"], s_ = {
  key: 2,
  class: "flow-tab-wrapper"
}, l_ = { class: "lib-install-intro" }, r_ = { class: "lib-install-pkg-label" }, i_ = ["disabled"], u_ = { class: "lib-install-pkg-hint" }, c_ = {
  key: 0,
  class: "lib-install-error",
  role: "alert"
}, d_ = /* @__PURE__ */ je({
  __name: "RobotEditor",
  props: {
    content: {},
    readonly: { type: Boolean },
    height: {}
  },
  emits: ["save", "update:content", "dirty"],
  setup(e, { expose: n, emit: r }) {
    const c = e, g = r, { t: a } = mt(), _ = Da(), y = N("code"), k = N(null), A = N(null), V = ps(null), E = N(""), B = N(!1), I = N(!1), j = N(!1), ue = N(!1), M = N(/* @__PURE__ */ new Set()), J = N(/* @__PURE__ */ new Set());
    function ae() {
      B.value = !1, I.value = !1, j.value = !1, ue.value = !1, M.value.clear(), J.value.clear();
    }
    function z() {
      B.value = !0, I.value = !0, j.value = !0, ue.value = !0;
      for (let f = 0; f < Q.testCases.length; f++) M.value.add(f);
      for (let f = 0; f < Q.keywords.length; f++) J.value.add(f);
    }
    const D = N(/* @__PURE__ */ new Set());
    function R(f, h, d, v) {
      return !!v || D.value.has(`${f}-${h}-${d}`);
    }
    function H(f, h, d) {
      const v = `${f}-${h}-${d}`;
      D.value.has(v) ? D.value.delete(v) : D.value.add(v);
    }
    const Y = N(null), U = N(""), Z = N(-1), ce = N(null), ke = N([]), ee = gn(/* @__PURE__ */ new Map());
    let W = null;
    const oe = N([]), fe = N(-1), be = N(null), Fe = gn(/* @__PURE__ */ new Map());
    function Pe(f) {
      if (!f.trim()) return { keyword: "", args: [] };
      const h = f.split(/  +|\t+/).map((d) => d.trim()).filter(Boolean);
      return { keyword: h[0] || "", args: h.slice(1) };
    }
    function He(f, h) {
      return [f, ...h].filter(Boolean).join("    ");
    }
    function ie(f, h, d, v) {
      const l = `${f}-${h}-${d}`;
      if (!Fe.has(l)) {
        const S = Pe(v[d]);
        Fe.set(l, {
          type: "keyword",
          keyword: S.keyword,
          args: S.args,
          returnVars: [],
          condition: "",
          loopVar: "",
          loopFlavor: "",
          loopValues: [],
          exceptPattern: "",
          exceptVar: "",
          varScope: "",
          comment: ""
        });
      }
      return Fe.get(l);
    }
    function Ie(f, h, d, v) {
      const l = `${f}-${h}-${d}`, S = Fe.get(l);
      S && (v[d] = He(S.keyword, S.args));
    }
    function tt(f, h, d, v) {
      Ie(f, h, d, v);
      const l = ie(f, h, d, v);
      zt(l);
    }
    function gt(f, h, d, v) {
      const l = ie(f, h, d, v);
      qt(l);
    }
    function ut(f, h, d, v, l) {
      const S = ie(f, h, d, v);
      Gt(S, l), Ie(f, h, d, v);
    }
    function Ze(f, h, d, v) {
      ie(f, h, d, v).args.push(""), Ie(f, h, d, v);
    }
    function vt(f, h, d, v, l) {
      ie(f, h, d, v).args.splice(l, 1), Ie(f, h, d, v);
    }
    function ze(f, h, d, v) {
      Ie(f, h, d, v);
    }
    const Q = gn({
      settings: [],
      variables: [],
      testCases: [],
      keywords: [],
      preambleLines: []
    }), ot = O(() => {
      var f;
      return ((f = _.filePath) == null ? void 0 : f.toLowerCase().endsWith(".resource")) ?? !1;
    }), pe = O(() => Q.testCases.length), se = O(() => Q.keywords.length), me = O(
      () => fm(_.filePath) && (Q.testCases.length > 0 || mm(E.value))
    );
    function Se(f) {
      try {
        Fe.clear();
        const h = gm(f);
        return Q.settings = h.settings, Q.variables = h.variables, Q.testCases = h.testCases, Q.keywords = h.keywords, Q.preambleLines = h.preambleLines, Q.trailingComments = h.trailingComments, k.value = null, !0;
      } catch (h) {
        return k.value = h instanceof Error ? h.message : "Failed to parse Robot Framework file", !1;
      }
    }
    function Be(f, h) {
      for (const d of f)
        (d.type === "keyword" || d.type === "assignment") && d.keyword.trim() && h.add(d.keyword.trim());
    }
    async function ht() {
      var v;
      const f = /* @__PURE__ */ new Set();
      for (const l of Q.testCases) Be(l.steps, f);
      for (const l of Q.keywords) Be(l.steps, f);
      const h = [];
      for (const l of f) {
        const S = l.toLowerCase().trim(), b = S.includes(".") ? S.substring(S.indexOf(".") + 1).trim() : S;
        _o.has(S) || _o.has(b) || Q.keywords.some((o) => o.name.toLowerCase() === S || o.name.toLowerCase() === b) || ee.has(S) || ee.has(b) || h.push(l);
      }
      if (h.length === 0) return;
      const d = /* @__PURE__ */ new Set();
      for (const l of h) {
        const S = l.toLowerCase().trim();
        if (!d.has(S)) {
          d.add(S);
          try {
            const b = await Oo(l, _.scopeId ?? void 0);
            for (const o of b.results)
              if ((v = o.args) != null && v.length) {
                const s = o.name.toLowerCase().trim();
                ee.set(s, o.args), s.includes(".") && ee.set(s.substring(s.indexOf(".") + 1).trim(), o.args);
              }
          } catch {
          }
        }
      }
    }
    function Bt() {
      return vm(Q, { isResource: ot.value });
    }
    function Vt(f) {
      if (f !== y.value)
        if (f === "code")
          E.value = Bt(), y.value = "code", k.value = null, Xe(() => to());
        else {
          if (y.value === "code") {
            const h = yt();
            Se(h), bt();
          }
          y.value = f;
        }
    }
    function yt() {
      var f;
      return ((f = V.value) == null ? void 0 : f.state.doc.toString()) ?? E.value;
    }
    function to() {
      if (bt(), !A.value) return;
      const f = la.create({
        doc: E.value,
        extensions: [
          // EDT-05 / FR-v0.3-16：只读时 CodeMirror 本身也拒绝键入。
          la.readOnly.of(!!c.readonly),
          pn.editable.of(!c.readonly),
          ks(),
          Cs(),
          $s(),
          Es(),
          Ts.of([...Ss, ...Ls]),
          // 平台品牌高亮样式（EDT-07）；未覆盖的 tag 回落到 CodeMirror 默认配色。
          sa(nm, { fallback: !0 }),
          sa(_s, { fallback: !0 }),
          new ws(om()),
          pn.updateListener.of((h) => {
            if (h.docChanged) {
              const d = h.state.doc.toString();
              E.value = d, At(d);
            }
          }),
          pn.theme({
            "&": { height: "100%", fontSize: "13px" },
            ".cm-content": { fontFamily: "'Fira Code', 'Consolas', 'Monaco', monospace", padding: "8px 0" },
            ".cm-gutters": { background: "#f8f9fa", borderRight: "1px solid #e0e0e0", color: "#999" },
            ".cm-activeLine": { background: "rgba(60, 181, 161, 0.06)" },
            ".cm-activeLineGutter": { background: "rgba(60, 181, 161, 0.1)" }
          })
        ]
      });
      V.value = new pn({ state: f, parent: A.value });
    }
    function bt() {
      V.value && (V.value.destroy(), V.value = null);
    }
    let oo = "", Kt = "";
    function At(f) {
      c.readonly || (g("update:content", f), g("dirty", f !== Kt));
    }
    function Nt() {
      const f = Bt();
      oo = f, At(f);
    }
    const wt = O(() => y.value === "visual" || y.value === "flow");
    Le(() => Q.settings, () => {
      wt.value && Nt();
    }, { deep: !0 }), Le(() => Q.variables, () => {
      wt.value && Nt();
    }, { deep: !0 }), Le(() => Q.testCases, () => {
      wt.value && Nt();
    }, { deep: !0 }), Le(() => Q.keywords, () => {
      wt.value && Nt();
    }, { deep: !0 });
    const _t = O(() => {
      const f = es(Q), h = Xc(Q);
      return f === 0 && h === 0 || !ts(Q) && !os(Q) ? "hidden" : f > 0 ? "on" : "off";
    }), F = Qn();
    function w() {
      const f = _t.value;
      if (f === "hidden") return;
      const h = f === "on" ? "disable" : "enable", { form: d, changedKeywords: v } = Zc(Q, h);
      if (Q.settings = d.settings, Q.testCases = d.testCases, Q.keywords = d.keywords, y.value === "code") {
        const l = Bt();
        if (E.value = l, V.value) {
          const S = V.value.state.doc.toString();
          S !== l && V.value.dispatch({
            changes: { from: 0, to: S.length, insert: l }
          });
        }
        At(l);
      }
      if (v > 0) {
        const l = h === "enable" ? "robotEditor.heal.toastEnabled" : "robotEditor.heal.toastDisabled";
        F.success(a(l, { count: v }));
      }
    }
    const P = N(null), G = N(!1), ge = N(null);
    let Ve = 0;
    async function nt() {
      if (P.value = null, G.value = !1, ge.value = null, _.scopeId == null || !_.filePath || !_.filePath.toLowerCase().endsWith(".robot")) return;
      const f = ++Ve, h = _.filePath, d = _.scopeId, v = await am(d, h);
      f === Ve && (P.value = v, v && (ge.value = h));
    }
    function Ht(f) {
      f === P.value && (G.value = !0);
    }
    async function Go(f) {
      if (!(!P.value || ge.value == null || _.scopeId == null)) {
        if (f != null && Array.isArray(P.value.commands)) {
          const h = hm(f), d = P.value.commands.length, v = P.value.commands.filter(
            (l) => !l.id || h.has(l.id)
          );
          v.length !== d && (P.value.commands = v, G.value = !0);
        }
        G.value && (await sm(_.scopeId, ge.value, P.value), G.value = !1);
      }
    }
    n({ saveSidecarIfDirty: Go }), Le(() => [_.scopeId, _.filePath], nt, { immediate: !0 });
    const Ae = zo(), io = Ka(), Ge = oa(), To = Qn();
    function Yo() {
      _.scopeId != null && Ae.refreshKeywords(_.scopeId).catch(() => {
      });
    }
    const uo = {
      // Default to `-batteries` for the install suggestion: it bundles
      // Playwright + Node.js, so the user doesn't need a separate Node
      // install on the env host. The plain `robotframework-browser`
      // requires `rfbrowser init` to download Node + Playwright first
      // and tends to surprise users on freshly-built envs. Both stay
      // in the providers list so `_isLibraryProvided` recognises
      // either as already-installed.
      browser: ["robotframework-browser-batteries", "robotframework-browser"],
      seleniumlibrary: ["robotframework-seleniumlibrary"],
      requestslibrary: ["robotframework-requests"],
      databaselibrary: ["robotframework-databaselibrary"],
      sshlibrary: ["robotframework-sshlibrary"],
      ftplibrary: ["robotframework-ftplibrary"],
      excellibrary: ["robotframework-excellibrary"],
      jsonlibrary: ["robotframework-jsonlibrary"],
      appiumlibrary: ["robotframework-appiumlibrary"],
      swinglibrary: ["robotframework-swinglibrary"],
      sikulilibrary: ["robotframework-sikulilibrary"],
      imaplibrary: ["robotframework-imaplibrary"],
      archivelibrary: ["robotframework-archivelibrary"],
      cryptolibrary: ["robotframework-crypto"],
      restinstance: ["RESTinstance"]
    };
    function En(f) {
      const h = f.toLowerCase(), d = uo[h];
      return (d == null ? void 0 : d[0]) ?? `robotframework-${h}`;
    }
    function Sn(f, h) {
      const d = f.toLowerCase(), v = uo[d];
      if (!v) return !1;
      const l = new Set(h.map((S) => S.name.toLowerCase()));
      return v.some((S) => l.has(S.toLowerCase()));
    }
    const ct = N(!1), Rt = N(""), Ft = N(""), co = N(null), Xo = N(""), Ot = N(!1), It = N(null);
    function Ln(f) {
      var b;
      if (_.scopeId == null) return;
      const h = io.getRepo(_.scopeId), d = (h == null ? void 0 : h.environment_id) ?? null, v = Ge.environments.find((o) => o.is_default), l = d ?? (v == null ? void 0 : v.id) ?? ((b = Ge.environments[0]) == null ? void 0 : b.id) ?? null;
      if (l == null) {
        To.warning(
          a("robotEditor.libraryNotInEnvTitle", { library: f }),
          a("robotEditor.libraryNoEnvAtAllBody", { library: f })
        );
        return;
      }
      const S = Ge.environments.find((o) => o.id === l);
      Rt.value = f, Ft.value = En(f), co.value = l, Xo.value = (S == null ? void 0 : S.name) ?? "", It.value = null, ct.value = !0;
    }
    async function Vn() {
      if (co.value == null) return;
      const f = Ft.value.trim();
      if (f) {
        Ot.value = !0, It.value = null;
        try {
          await Ba(co.value, { package_name: f }), Yo(), To.success(
            a("robotEditor.libraryInstallStartedTitle"),
            a("robotEditor.libraryInstallStartedBody", { package: f })
          ), ct.value = !1;
        } catch (h) {
          It.value = Co(
            h,
            a("robotEditor.libraryInstallFailed")
          );
        } finally {
          Ot.value = !1;
        }
      }
    }
    function An() {
      ct.value = !1;
    }
    async function kt(f) {
      var o, s;
      if (_.scopeId == null) return;
      try {
        await Ae.refreshKeywords(_.scopeId);
      } catch {
        return;
      }
      if (!f) return;
      const h = f.toLowerCase();
      if (Ae.keywords.some(
        ($) => ($.library || "").toLowerCase() === h
      )) return;
      if (Ge.environments.length === 0)
        try {
          await Ge.fetchEnvironments();
        } catch {
        }
      const v = io.getRepo(_.scopeId), l = (v == null ? void 0 : v.environment_id) ?? null, S = Ge.environments.find(($) => $.is_default), b = l ?? (S == null ? void 0 : S.id) ?? ((o = Ge.environments[0]) == null ? void 0 : o.id) ?? null;
      if (b != null)
        try {
          const $ = await El(b);
          if (Sn(f, $)) {
            const K = ((s = uo[h]) == null ? void 0 : s.find(
              (te) => $.some((C) => C.name.toLowerCase() === te.toLowerCase())
            )) ?? "?";
            To.info(
              a("robotEditor.libraryAlreadyProvidedTitle", { library: f }),
              a("robotEditor.libraryAlreadyProvidedBody", {
                library: f,
                provider: K
              })
            );
            return;
          }
        } catch {
        }
      Ln(f);
    }
    Le(
      () => _.filePath,
      (f, h) => {
        !f || f === h || _.scopeId != null && Ae.preloadKeywords(_.scopeId).catch(() => {
        });
      }
    );
    const Eo = [
      "BuiltIn",
      "Collections",
      "String",
      "OperatingSystem",
      "Process",
      "DateTime",
      "XML",
      "Dialogs",
      "Screenshot",
      "Telnet",
      "Remote",
      "SeleniumLibrary",
      "Browser",
      "RequestsLibrary",
      "DatabaseLibrary",
      "SSHLibrary",
      "FTPLibrary",
      "ExcelLibrary",
      "JSONLibrary",
      "AppiumLibrary",
      "SwingLibrary",
      "SikuliLibrary",
      "ImapLibrary",
      "ArchiveLibrary",
      "CryptoLibrary",
      "RESTinstance"
    ], Pt = N(null), po = N(""), xe = N(-1), So = N(null), Lo = O(() => {
      const f = po.value.toLowerCase().trim();
      return Eo.filter((h) => !f || h.toLowerCase().includes(f)).slice(0, 15);
    });
    function Nn(f) {
      Pt.value = f, po.value = Q.settings[f].value, xe.value = -1;
    }
    function Ne(f) {
      var d;
      const h = f.relatedTarget;
      h && ((d = So.value) != null && d.contains(h)) || setTimeout(() => {
        Pt.value = null;
      }, 150);
    }
    function Vo(f) {
      po.value = Q.settings[f].value, xe.value = -1, Pt.value = f;
    }
    function Ue(f, h) {
      const d = Lo.value;
      if (f.key === "Enter") {
        f.preventDefault(), xe.value >= 0 && xe.value < d.length && Zo(h, d[xe.value]);
        return;
      }
      d.length && (f.key === "ArrowDown" ? (f.preventDefault(), Pt.value = h, xe.value = Math.min(xe.value + 1, d.length - 1)) : f.key === "ArrowUp" ? (f.preventDefault(), xe.value = Math.max(xe.value - 1, 0)) : f.key === "Escape" && (Pt.value = null, xe.value = -1));
    }
    function Zo(f, h) {
      Q.settings[f].value = h, Pt.value = null, xe.value = -1, po.value = "";
    }
    const Rn = [
      "Documentation",
      "Library",
      "Resource",
      "Variables",
      "Suite Setup",
      "Suite Teardown",
      "Test Setup",
      "Test Teardown",
      "Test Template",
      "Test Timeout",
      "Force Tags",
      "Default Tags",
      "Metadata"
    ];
    function Fn() {
      Q.settings.push({ key: "Library", value: "", args: [] });
    }
    function Ao(f) {
      Q.settings.splice(f, 1);
    }
    function Pn(f) {
      Q.settings[f].args.push("");
    }
    function fo(f, h) {
      Q.settings[f].args.splice(h, 1);
    }
    function Dn() {
      Q.variables.push({ name: "${NEW_VAR}", value: "" });
    }
    function Qo(f) {
      Q.variables.splice(f, 1);
    }
    function no(f, h = "keyword") {
      f.push(Bo(h));
    }
    function Ct(f, h) {
      const d = Bo(h);
      f.push(d), f.push(Bo("keyword")), f.push(Bo("end"));
    }
    function mo(f, h) {
      f.splice(h, 1);
    }
    function go(f, h, d) {
      const v = h + d;
      if (v < 0 || v >= f.length) return;
      const l = f[h];
      f[h] = f[v], f[v] = l;
    }
    function dt(f) {
      f.args.push("");
    }
    function $t(f) {
      const h = $e(f);
      if (h.length === 0) return "+";
      const d = f.args.length;
      if (d < h.length)
        return "+ " + h[d];
      const v = h[h.length - 1];
      return v != null && v.startsWith("*") && !v.startsWith("**") ? "+ ..." : "+";
    }
    function Qe(f, h) {
      f.args.splice(h, 1);
    }
    function at(f) {
      f.returnVars.push("${var}");
    }
    function Jo(f, h) {
      f.returnVars.splice(h, 1);
    }
    function ao(f) {
      f.loopValues.push("");
    }
    function No(f, h) {
      f.loopValues.splice(h, 1);
    }
    function vo(f) {
      f.type === "keyword" ? f.returnVars = [] : f.type === "assignment" ? f.returnVars.length === 0 && (f.returnVars = ["${result}"]) : f.type === "for" ? (f.loopVar || (f.loopVar = "${item}"), f.loopFlavor || (f.loopFlavor = "IN")) : f.type === "var" ? (f.returnVars.length === 0 && (f.returnVars = ["${var}"]), f.args.length === 0 && (f.args = [""])) : f.type === "comment" && (f.comment || (f.comment = "# "));
    }
    function en(f) {
      return a("robotEditor.stepType." + f);
    }
    function Wt(f) {
      switch (f) {
        case "Library":
          return "stype-library";
        case "Resource":
          return "stype-resource";
        case "Variables":
          return "stype-variables";
        case "Documentation":
          return "stype-doc";
        case "Suite Setup":
        case "Test Setup":
          return "stype-setup";
        case "Suite Teardown":
        case "Test Teardown":
          return "stype-teardown";
        case "Test Template":
        case "Test Timeout":
          return "stype-config";
        case "Force Tags":
        case "Default Tags":
          return "stype-tags";
        case "Metadata":
          return "stype-meta";
        default:
          return "";
      }
    }
    function Ro(f) {
      switch (f) {
        case "keyword":
          return "type-keyword";
        case "assignment":
          return "type-assign";
        case "var":
          return "type-var";
        case "for":
        case "while":
          return "type-loop";
        case "if":
        case "else_if":
        case "else":
          return "type-condition";
        case "try":
        case "except":
        case "finally":
          return "type-error";
        case "end":
        case "break":
        case "continue":
        case "return":
          return "type-flow";
        case "comment":
          return "type-comment";
        default:
          return "";
      }
    }
    function Mn(f) {
      return ["for", "if", "else_if", "else", "while", "try", "except", "finally"].includes(f);
    }
    function tn(f) {
      return f === "end";
    }
    function st(f, h) {
      let d = 0;
      for (let v = 0; v < h; v++)
        Mn(f[v].type) && d++, tn(f[v].type) && (d = Math.max(0, d - 1));
      return tn(f[h].type) && (d = Math.max(0, d - 1)), ["else", "else_if", "except", "finally"].includes(f[h].type) && (d = Math.max(0, d - 1)), d;
    }
    const jt = O(() => {
      const f = [];
      for (const h of _o.keys()) {
        const d = h.replace(/\b\w/g, (v) => v.toUpperCase());
        f.push({ name: d, source: "builtin", args: _o.get(h) });
      }
      return f;
    }), De = O(() => {
      const f = U.value.toLowerCase().trim(), h = /* @__PURE__ */ new Set(), d = [];
      for (const v of ke.value) {
        const l = v.name.toLowerCase();
        if (!h.has(l)) {
          h.add(l);
          const S = v.library || "project";
          if (d.push({ name: v.name, source: S, args: v.args }), S !== "project" && S !== "local" && S !== "builtin" && !S.includes("Library")) {
            const b = S + "." + v.name, o = b.toLowerCase();
            h.has(o) || (h.add(o), d.push({ name: b, source: S, args: v.args }));
          }
        }
      }
      for (const v of Q.keywords) {
        const l = v.name.toLowerCase();
        v.name && !h.has(l) && (h.add(l), d.push({ name: v.name, source: "local", args: v.arguments }));
      }
      for (const v of jt.value) {
        const l = v.name.toLowerCase();
        h.has(l) || (h.add(l), d.push(v));
      }
      return d.filter((v) => {
        if (!f) return !0;
        const l = v.name.toLowerCase(), S = l.includes(".") ? l.substring(l.indexOf(".") + 1) : l;
        return l.includes(f) || S.includes(f);
      }).slice(0, 15);
    });
    function qt(f) {
      Y.value = f, U.value = f.keyword, Z.value = -1;
    }
    function pt(f) {
      var d;
      const h = f.relatedTarget;
      h && ((d = ce.value) != null && d.contains(h)) || setTimeout(() => {
        Y.value = null;
      }, 150);
    }
    function zt(f) {
      U.value = f.keyword, Z.value = -1, Y.value = f, on(f.keyword);
    }
    function on(f) {
      W && clearTimeout(W);
      const h = f.trim();
      if (h.length < 2 && !/[^\x00-\x7F]/.test(h)) {
        ke.value = [];
        return;
      }
      W = setTimeout(async () => {
        var d;
        try {
          const v = await Oo(f.trim(), _.scopeId ?? void 0);
          ke.value = v.results.slice(0, 15);
          for (const l of ke.value)
            (d = l.args) != null && d.length && ee.set(l.name.toLowerCase().trim(), l.args);
        } catch {
          ke.value = [];
        }
      }, 300);
    }
    function Tt(f, h) {
      const d = De.value;
      if (f.key === "Enter") {
        f.preventDefault(), Z.value >= 0 && Z.value < d.length && Gt(h, d[Z.value]);
        return;
      }
      d.length && (f.key === "ArrowDown" ? (f.preventDefault(), Y.value = h, Z.value = Math.min(Z.value + 1, d.length - 1)) : f.key === "ArrowUp" ? (f.preventDefault(), Z.value = Math.max(Z.value - 1, 0)) : f.key === "Escape" && (Y.value = null, Z.value = -1));
    }
    function Gt(f, h) {
      var l;
      if (f.keyword = h.name, (l = h.args) != null && l.length) {
        const S = h.name.toLowerCase().trim();
        ee.set(S, h.args), S.includes(".") && ee.set(S.substring(S.indexOf(".") + 1).trim(), h.args);
      }
      Y.value = null, Z.value = -1, U.value = "";
      const v = $e(f).filter((S) => !S.endsWith("?") && !S.startsWith("*")).length;
      for (; f.args.length < v; )
        f.args.push("");
    }
    function nn() {
      const f = [];
      for (const h of Q.variables) h.name && f.push(h.name);
      for (const h of Q.keywords)
        for (const d of h.arguments)
          d && f.push(d.replace(/=.*$/, ""));
      return f;
    }
    function Dt(f, h) {
      if (be.value = h, !f.includes("$")) {
        oe.value = [];
        return;
      }
      const d = f.match(/[$@&%]\{([^}]*)$/);
      if (!d) {
        oe.value = [];
        return;
      }
      const v = d[1].toLowerCase(), l = nn();
      oe.value = l.filter((S) => S.toLowerCase().includes(v)).slice(0, 8), fe.value = -1;
    }
    function Yt(f, h, d) {
      const l = (f.args[h] || "").replace(/[$@&%]\{[^}]*$/, d + "}");
      f.args[h] = l.includes("}") ? l : d, oe.value = [], be.value = null;
    }
    function Mt(f, h, d) {
      oe.value.length && (f.key === "ArrowDown" ? (f.preventDefault(), fe.value = Math.min(fe.value + 1, oe.value.length - 1)) : f.key === "ArrowUp" ? (f.preventDefault(), fe.value = Math.max(fe.value - 1, 0)) : f.key === "Enter" && fe.value >= 0 ? (f.preventDefault(), Yt(h, d, oe.value[fe.value])) : f.key === "Escape" && (oe.value = [], be.value = null));
    }
    function ho() {
      setTimeout(() => {
        oe.value = [], be.value = null;
      }, 200);
    }
    function Te(f) {
      return f ? f.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/([$@&])\{([^}]+)\}/g, '<span class="hl-var">$1{$2}</span>').replace(/%\{([^}]+)\}/g, '<span class="hl-env">%{$1}</span>') : "";
    }
    function Xt(f) {
      var v;
      const h = f.target, d = (v = h.parentElement) == null ? void 0 : v.querySelector(".arg-highlight-overlay, .form-hl-overlay");
      d && (d.scrollLeft = h.scrollLeft);
    }
    function lt(f) {
      const h = Math.max((f || "").length, 6);
      return Math.min(h, 30) + "ch";
    }
    function Fo(f, h) {
      let d = f.arguments[h];
      if (!d || (d = d.trim(), !d) || d === "@{}" || /^[$@&]\{[^}]*\}(=.*)?$/.test(d)) return;
      const v = d.startsWith("@{") ? "@{" : d.startsWith("&{") ? "&{" : "${";
      let l = d.replace(/^[$@&%]\{/, "").replace(/\}(=.*)?$/, ""), S = "";
      const b = d.match(/\}=(.*)$/);
      if (b)
        S = "=" + b[1];
      else {
        const o = l.indexOf("=");
        o > 0 && (S = l.substring(o), l = l.substring(0, o));
      }
      l = l.replace(/[$@&%{}]/g, "").trim(), l && (f.arguments[h] = v + l + "}" + S);
    }
    function xn(f) {
      if (!f.name || f.name === "#") return;
      let h = f.name.trim();
      if (!h || /^[$@&%]\{[^}]+\}$/.test(h)) return;
      let d = "${";
      h.startsWith("@") ? d = "@{" : h.startsWith("&") ? d = "&{" : h.startsWith("%") && (d = "%{"), h = h.replace(/^[$@&%]\{?/, "").replace(/\}$/, "").replace(/[$@&%{}]/g, "").trim(), h && (f.name = d + h + "}");
    }
    function yo(f, h) {
      let d = f.returnVars[h];
      if (!d || (d = d.trim(), !d) || /^[$@&%]\{[^}]+\}$/.test(d)) return;
      let v = "${";
      d.startsWith("@") ? v = "@{" : d.startsWith("&") ? v = "&{" : d.startsWith("%") && (v = "%{"), d = d.replace(/^[$@&%]\{?/, "").replace(/\}$/, "").replace(/[$@&%{}]/g, "").trim(), d && (f.returnVars[h] = v + d + "}");
    }
    function an(f) {
      if (f.startsWith("@{")) return "*" + f.replace(/^@\{([^}]*)\}.*$/, "$1");
      if (f.startsWith("&{")) return "**" + f.replace(/^&\{([^}]*)\}.*$/, "$1");
      const h = f.replace(/^[$@&%]\{([^}]+)\}.*$/, "$1").replace(/=.*$/, "");
      return f.includes("=") ? h + "?" : h;
    }
    function $e(f) {
      var v;
      const h = f.keyword.toLowerCase().trim(), d = h.includes(".") ? h.substring(h.indexOf(".") + 1).trim() : h;
      for (const l of [h, d]) {
        const S = _o.get(l);
        if (S) return S;
        const b = Q.keywords.find((s) => s.name.toLowerCase() === l);
        if ((v = b == null ? void 0 : b.arguments) != null && v.length)
          return b.arguments.filter((s) => s !== "@{}").map(an);
        const o = ee.get(l);
        if (o != null && o.length)
          return o.filter((s) => s !== "@{}").map(an);
      }
      return [];
    }
    function bo() {
      Q.testCases.push({
        name: "",
        documentation: "",
        tags: [],
        setup: "",
        teardown: "",
        timeout: "",
        template: "",
        steps: []
      });
    }
    function sn(f) {
      Q.testCases.splice(f, 1), M.value.delete(f);
    }
    function Un(f) {
      M.value.has(f) ? M.value.delete(f) : M.value.add(f);
    }
    const rt = N(/* @__PURE__ */ new Map());
    function Bn(f, h) {
      h.trim() && !Q.testCases[f].tags.includes(h.trim()) && Q.testCases[f].tags.push(h.trim());
    }
    function Kn(f, h) {
      Q.testCases[f].tags.splice(h, 1);
    }
    function Po(f) {
      Bn(f, rt.value.get(f) || ""), rt.value.set(f, "");
    }
    function Zt() {
      Q.keywords.push({
        name: "",
        documentation: "",
        arguments: [],
        tags: [],
        setup: "",
        teardown: "",
        timeout: "",
        returnValue: "",
        steps: []
      });
    }
    function ln(f) {
      Q.keywords.splice(f, 1), J.value.delete(f);
    }
    function rn(f) {
      J.value.has(f) ? J.value.delete(f) : J.value.add(f);
    }
    N(/* @__PURE__ */ new Map());
    function Do(f, h) {
      Q.keywords[f].arguments.splice(h, 1);
    }
    function xt(f, h) {
      const d = Q.keywords[f];
      switch (h) {
        case "positional":
          d.arguments.push("${arg}");
          break;
        case "optional":
          d.arguments.push("${arg}=");
          break;
        case "varargs":
          d.arguments.some((v) => v.startsWith("@{")) || d.arguments.push("@{args}");
          break;
        case "kwargs":
          d.arguments.some((v) => v.startsWith("&{")) || d.arguments.push("&{kwargs}");
          break;
        case "named-only":
          d.arguments.some((v) => v.startsWith("@{")) || d.arguments.push("@{}"), d.arguments.push("${arg}=");
          break;
      }
    }
    function Hn(f) {
      return f === "@{}" ? "named-only-sep" : f.startsWith("@{") ? "varargs" : f.startsWith("&{") ? "kwargs" : f.includes("=") ? "optional" : "positional";
    }
    function Mo(f) {
      return f.replace(/^[$@&%]\{([^}]*)\}.*$/, "$1");
    }
    function un(f) {
      const h = f.match(/\}=(.*)$/);
      return h ? h[1] : "";
    }
    function wo(f, h, d) {
      var o, s;
      const v = f.arguments[h], l = ((o = v.match(/^([$@&%]\{)/)) == null ? void 0 : o[1]) || "${", S = ((s = v.match(/(\}=?.*)$/)) == null ? void 0 : s[1]) || "}", b = d.replace(/[$@&%{}]/g, "");
      f.arguments[h] = l + b + S;
    }
    function cn(f, h, d) {
      const l = f.arguments[h].replace(/\}=.*$/, "}");
      f.arguments[h] = l + "=" + d;
    }
    function xo(f, h) {
      for (let d = 0; d < h; d++)
        if (f.arguments[d].startsWith("@{")) return !0;
      return !1;
    }
    const so = N(/* @__PURE__ */ new Map());
    function On(f, h) {
      h.trim() && !Q.keywords[f].tags.includes(h.trim()) && Q.keywords[f].tags.push(h.trim());
    }
    function Qt(f, h) {
      Q.keywords[f].tags.splice(h, 1);
    }
    function dn(f) {
      On(f, so.value.get(f) || ""), so.value.set(f, "");
    }
    const Ke = N(null);
    let Jt = null, ft = null;
    function Je() {
      if (!Ke.value) return;
      const f = Ke.value.querySelectorAll(".step-row");
      for (const h of f) {
        const d = h, v = d.scrollHeight > 40;
        d.classList.toggle("args-vertical", v);
      }
    }
    function lo() {
      Ke.value && (Jt = new MutationObserver(() => Xe(Je)), Jt.observe(Ke.value, { childList: !0, subtree: !0 }), ft = new ResizeObserver(() => Je()), ft.observe(Ke.value), Xe(Je));
    }
    function In() {
      Jt == null || Jt.disconnect(), ft == null || ft.disconnect(), Jt = null, ft = null;
    }
    return qo(() => {
      Se(c.content), E.value = c.content, Kt = c.content, ht(), Xe(lo);
    }), Ho(() => {
      bt(), In();
    }), Le(() => c.content, (f) => {
      if (f !== oo) {
        if (Kt = f, g("dirty", !1), y.value === "visual" || y.value === "flow")
          Se(f), ht();
        else if (E.value = f, V.value) {
          const h = V.value.state.doc.toString();
          h !== f && V.value.dispatch({ changes: { from: 0, to: h.length, insert: f } });
        }
      }
    }), (f, h) => (i(), u("div", {
      class: ne(["robot-editor", { "robot-editor--readonly": e.readonly }]),
      style: Ee(e.height ? { height: e.height } : void 0)
    }, [
      e.readonly ? (i(), Ut(zf, {
        key: 0,
        role: "viewer"
      })) : T("", !0),
      t("div", ym, [
        t("div", bm, [
          t("button", {
            class: ne(["tab-btn", { active: y.value === "code" }]),
            onClick: h[0] || (h[0] = (d) => Vt("code"))
          }, m(p(a)("robotEditor.codeTab")), 3),
          t("button", {
            class: ne(["tab-btn", { active: y.value === "visual" }]),
            onClick: h[1] || (h[1] = (d) => Vt("visual"))
          }, m(p(a)("robotEditor.visualTab")), 3),
          t("button", {
            class: ne(["tab-btn", { active: y.value === "flow" }]),
            onClick: h[2] || (h[2] = (d) => Vt("flow"))
          }, m(p(a)("robotEditor.flowTab")), 3)
        ]),
        t("div", wm, [
          t("span", _m, m(ot.value ? ".resource" : ".robot"), 1),
          !ot.value && pe.value > 0 ? (i(), u("span", km, m(pe.value) + " " + m(p(a)("robotEditor.tests")), 1)) : T("", !0),
          se.value > 0 ? (i(), u("span", Cm, m(se.value) + " " + m(p(a)("robotEditor.keywordsCount")), 1)) : T("", !0),
          me.value ? (i(), u("span", {
            key: 2,
            class: "badge badge-warning",
            "data-testid": "init-file-warning",
            title: p(a)("robotEditor.initFileWarning")
          }, m(p(a)("robotEditor.initFileWarning")), 9, $m)) : T("", !0),
          _t.value !== "hidden" ? (i(), u("button", {
            key: 3,
            class: ne(["heal-toggle-btn", { "heal-toggle-btn--on": _t.value === "on" }]),
            "data-testid": "suite-heal-toggle",
            title: _t.value === "on" ? p(a)("robotEditor.heal.toggleOnHint") : p(a)("robotEditor.heal.toggleOffHint"),
            onClick: w
          }, [
            h[9] || (h[9] = t("span", { "aria-hidden": "true" }, "🩹", -1)),
            we(" " + m(_t.value === "on" ? p(a)("robotEditor.heal.toggleOn") : p(a)("robotEditor.heal.toggleOff")), 1)
          ], 10, Tm)) : T("", !0),
          y.value === "visual" ? (i(), u("button", {
            key: 4,
            class: "icon-btn",
            onClick: ae,
            title: p(a)("robotEditor.expandAll")
          }, "⊞", 8, Em)) : T("", !0),
          y.value === "visual" ? (i(), u("button", {
            key: 5,
            class: "icon-btn",
            onClick: z,
            title: p(a)("robotEditor.collapseAll")
          }, "⊟", 8, Sm)) : T("", !0)
        ])
      ]),
      k.value ? (i(), u("div", Lm, [
        t("span", null, m(p(a)("robotEditor.parseError")) + ":", 1),
        we(" " + m(k.value), 1)
      ])) : T("", !0),
      q(t("div", {
        ref_key: "visualEditorRef",
        ref: Ke,
        class: "visual-editor"
      }, [
        t("div", Vm, [
          t("div", {
            class: "section-header",
            onClick: h[3] || (h[3] = (d) => B.value = !B.value)
          }, [
            t("span", Am, m(B.value ? "▶" : "▼"), 1),
            t("h3", null, m(p(a)("robotEditor.settingsSection")), 1),
            t("span", Nm, m(Q.settings.filter((d) => d.key !== "#").length), 1),
            ve(Oe, {
              size: "sm",
              variant: "secondary",
              onClick: re(Fn, ["stop"])
            }, {
              default: _e(() => [
                we("+ " + m(p(a)("robotEditor.addSetting")), 1)
              ]),
              _: 1
            })
          ]),
          q(t("div", Rm, [
            Q.settings.length === 0 ? (i(), u("div", Fm, m(p(a)("robotEditor.noSettings")), 1)) : T("", !0),
            (i(!0), u(x, null, X(Q.settings, (d, v) => (i(), u("div", {
              key: v,
              class: "setting-row"
            }, [
              d.key === "#" ? (i(), u(x, { key: 0 }, [
                q(t("input", {
                  "onUpdate:modelValue": (l) => d.value = l,
                  class: "form-input flex-1",
                  placeholder: "# Comment"
                }, null, 8, Pm), [
                  [le, d.value]
                ]),
                t("button", {
                  class: "step-btn danger",
                  onClick: (l) => Ao(v),
                  title: p(a)("editor.common.delete")
                }, "×", 8, Dm)
              ], 64)) : (i(), u(x, { key: 1 }, [
                q(t("select", {
                  "onUpdate:modelValue": (l) => d.key = l,
                  class: ne(["form-input setting-type-select", Wt(d.key)])
                }, [
                  (i(), u(x, null, X(Rn, (l) => t("option", {
                    key: l,
                    value: l
                  }, m(p(a)("robotEditor.settingType." + l.replace(/ /g, ""))), 9, xm)), 64))
                ], 10, Mm), [
                  [it, d.key]
                ]),
                d.key === "Library" ? (i(), u("div", Um, [
                  q(t("input", {
                    "onUpdate:modelValue": (l) => d.value = l,
                    class: "form-input setting-library-input",
                    placeholder: p(a)("robotEditor.settingValuePlaceholder"),
                    onFocus: (l) => Nn(v),
                    onBlur: Ne,
                    onKeydown: (l) => Ue(l, v),
                    onInput: (l) => Vo(v)
                  }, null, 40, Bm), [
                    [le, d.value]
                  ]),
                  Pt.value === v && Lo.value.length > 0 ? (i(), u("div", {
                    key: 0,
                    ref_for: !0,
                    ref_key: "librarySettingDropdownRef",
                    ref: So,
                    class: "keyword-dropdown"
                  }, [
                    (i(!0), u(x, null, X(Lo.value, (l, S) => (i(), u("div", {
                      key: l,
                      class: ne(["keyword-dropdown-item", { active: S === xe.value }]),
                      onMousedown: re((b) => Zo(v, l), ["prevent"])
                    }, [
                      t("span", Hm, m(l), 1)
                    ], 42, Km))), 128))
                  ], 512)) : T("", !0)
                ])) : (i(), u("span", Om, [
                  t("span", {
                    class: "form-hl-overlay",
                    innerHTML: Te(d.value)
                  }, null, 8, Im),
                  q(t("input", {
                    "onUpdate:modelValue": (l) => d.value = l,
                    class: "form-input",
                    style: { width: "100%" },
                    placeholder: p(a)("robotEditor.settingValuePlaceholder"),
                    spellcheck: "false"
                  }, null, 8, Wm), [
                    [le, d.value]
                  ])
                ])),
                d.args.length > 0 ? (i(), u("div", jm, [
                  (i(!0), u(x, null, X(d.args, (l, S) => (i(), u("span", {
                    key: S,
                    class: "chip"
                  }, [
                    q(t("input", {
                      "onUpdate:modelValue": (b) => d.args[S] = b,
                      class: "chip-edit-input",
                      placeholder: p(a)("robotEditor.argPlaceholder")
                    }, null, 8, qm), [
                      [le, d.args[S]]
                    ]),
                    t("button", {
                      class: "chip-remove",
                      onClick: (b) => fo(v, S)
                    }, "×", 8, zm)
                  ]))), 128))
                ])) : T("", !0),
                t("button", {
                  class: "step-btn",
                  onClick: (l) => Pn(v),
                  title: "Add argument"
                }, "+", 8, Gm),
                t("button", {
                  class: "step-btn danger",
                  onClick: (l) => Ao(v),
                  title: p(a)("editor.common.delete")
                }, "×", 8, Ym)
              ], 64))
            ]))), 128))
          ], 512), [
            [eo, !B.value]
          ])
        ]),
        t("div", Xm, [
          t("div", {
            class: "section-header",
            onClick: h[4] || (h[4] = (d) => I.value = !I.value)
          }, [
            t("span", Zm, m(I.value ? "▶" : "▼"), 1),
            t("h3", null, m(p(a)("robotEditor.variablesSection")), 1),
            t("span", Qm, m(Q.variables.filter((d) => d.name !== "#").length), 1),
            ve(Oe, {
              size: "sm",
              variant: "secondary",
              onClick: re(Dn, ["stop"])
            }, {
              default: _e(() => [
                we("+ " + m(p(a)("robotEditor.addVariable")), 1)
              ]),
              _: 1
            })
          ]),
          q(t("div", Jm, [
            Q.variables.length === 0 ? (i(), u("div", eg, m(p(a)("robotEditor.noVariables")), 1)) : T("", !0),
            (i(!0), u(x, null, X(Q.variables, (d, v) => (i(), u("div", {
              key: v,
              class: "variable-row"
            }, [
              d.name === "#" ? (i(), u(x, { key: 0 }, [
                q(t("input", {
                  "onUpdate:modelValue": (l) => d.value = l,
                  class: "form-input flex-1",
                  placeholder: "# Comment"
                }, null, 8, tg), [
                  [le, d.value]
                ]),
                t("button", {
                  class: "step-btn danger",
                  onClick: (l) => Qo(v),
                  title: p(a)("editor.common.delete")
                }, "×", 8, og)
              ], 64)) : (i(), u(x, { key: 1 }, [
                t("span", ng, [
                  t("span", {
                    class: "form-hl-overlay hl-mono",
                    innerHTML: Te(d.name)
                  }, null, 8, ag),
                  q(t("input", {
                    "onUpdate:modelValue": (l) => d.name = l,
                    class: "form-input var-name-input",
                    placeholder: p(a)("robotEditor.variableNamePlaceholder"),
                    onBlur: (l) => xn(d),
                    spellcheck: "false"
                  }, null, 40, sg), [
                    [le, d.name]
                  ])
                ]),
                t("span", lg, [
                  t("span", {
                    class: "form-hl-overlay",
                    innerHTML: Te(d.value)
                  }, null, 8, rg),
                  q(t("input", {
                    "onUpdate:modelValue": (l) => d.value = l,
                    class: "form-input",
                    style: { width: "100%" },
                    placeholder: p(a)("robotEditor.variableValuePlaceholder"),
                    spellcheck: "false"
                  }, null, 8, ig), [
                    [le, d.value]
                  ])
                ]),
                t("button", {
                  class: "step-btn danger",
                  onClick: (l) => Qo(v),
                  title: p(a)("editor.common.delete")
                }, "×", 8, ug)
              ], 64))
            ]))), 128))
          ], 512), [
            [eo, !I.value]
          ])
        ]),
        ot.value ? T("", !0) : (i(), u("div", cg, [
          t("div", {
            class: "section-header",
            onClick: h[5] || (h[5] = (d) => j.value = !j.value)
          }, [
            t("span", dg, m(j.value ? "▶" : "▼"), 1),
            t("h3", null, m(p(a)("robotEditor.testCasesSection")), 1),
            t("span", pg, m(Q.testCases.length), 1),
            ve(Oe, {
              size: "sm",
              variant: "secondary",
              onClick: re(bo, ["stop"])
            }, {
              default: _e(() => [
                we("+ " + m(p(a)("robotEditor.addTestCase")), 1)
              ]),
              _: 1
            })
          ]),
          q(t("div", fg, [
            Q.testCases.length === 0 ? (i(), u("div", mg, m(p(a)("robotEditor.noTestCases")), 1)) : T("", !0),
            (i(!0), u(x, null, X(Q.testCases, (d, v) => (i(), u("div", {
              key: v,
              class: "item-card"
            }, [
              t("div", {
                class: "item-header",
                onClick: (l) => Un(v)
              }, [
                t("span", vg, m(M.value.has(v) ? "▶" : "▼"), 1),
                t("span", hg, m(d.name || p(a)("robotEditor.unnamedTestCase")), 1),
                t("span", yg, m(d.steps.length) + " " + m(p(a)("robotEditor.steps")), 1),
                t("button", {
                  class: "remove-btn",
                  onClick: re((l) => sn(v), ["stop"]),
                  title: p(a)("editor.common.delete")
                }, "🗑", 8, bg)
              ], 8, gg),
              q(t("div", wg, [
                t("div", _g, [
                  t("label", kg, [
                    we(m(p(a)("robotEditor.testCaseName")) + " ", 1),
                    h[10] || (h[10] = t("span", { class: "required" }, "*", -1))
                  ]),
                  q(t("input", {
                    "onUpdate:modelValue": (l) => d.name = l,
                    class: "form-input",
                    placeholder: p(a)("robotEditor.testCaseNamePlaceholder")
                  }, null, 8, Cg), [
                    [le, d.name]
                  ])
                ]),
                t("div", $g, [
                  t("label", Tg, m(p(a)("robotEditor.documentation")), 1),
                  q(t("textarea", {
                    "onUpdate:modelValue": (l) => d.documentation = l,
                    class: "form-input form-textarea",
                    rows: "2",
                    placeholder: p(a)("robotEditor.documentationPlaceholder")
                  }, null, 8, Eg), [
                    [le, d.documentation]
                  ])
                ]),
                R("tc", v, "tags", d.tags.length ? "x" : "") ? (i(), u("div", Sg, [
                  t("label", Lg, [
                    we(m(p(a)("robotEditor.tags")) + " ", 1),
                    d.tags.length ? T("", !0) : (i(), u("button", {
                      key: 0,
                      class: "meta-close-btn",
                      onClick: (l) => H("tc", v, "tags")
                    }, "×", 8, Vg))
                  ]),
                  t("div", Ag, [
                    (i(!0), u(x, null, X(d.tags, (l, S) => (i(), u("span", {
                      key: S,
                      class: "chip chip-tag"
                    }, [
                      we(m(l) + " ", 1),
                      t("button", {
                        class: "chip-remove",
                        onClick: (b) => Kn(v, S)
                      }, "×", 8, Ng)
                    ]))), 128)),
                    t("div", Rg, [
                      t("input", {
                        value: rt.value.get(v) || "",
                        onInput: (l) => rt.value.set(v, l.target.value),
                        class: "chip-input",
                        placeholder: p(a)("robotEditor.addTag"),
                        onKeydown: Ye(re((l) => Po(v), ["prevent"]), ["enter"])
                      }, null, 40, Fg),
                      (rt.value.get(v) || "").trim() ? (i(), u("button", {
                        key: 0,
                        class: "chip-add-btn",
                        onClick: (l) => Po(v)
                      }, "+", 8, Pg)) : T("", !0)
                    ])
                  ])
                ])) : T("", !0),
                t("div", Dg, [
                  R("tc", v, "tags", d.tags.length ? "x" : "") ? T("", !0) : (i(), u("button", {
                    key: 0,
                    class: "meta-toggle-btn mt-tags",
                    onClick: (l) => H("tc", v, "tags")
                  }, "+ Tags", 8, Mg)),
                  R("tc", v, "setup", d.setup) ? T("", !0) : (i(), u("button", {
                    key: 1,
                    class: "meta-toggle-btn mt-setup",
                    onClick: (l) => H("tc", v, "setup")
                  }, "+ Setup", 8, xg)),
                  R("tc", v, "teardown", d.teardown) ? T("", !0) : (i(), u("button", {
                    key: 2,
                    class: "meta-toggle-btn mt-teardown",
                    onClick: (l) => H("tc", v, "teardown")
                  }, "+ Teardown", 8, Ug)),
                  R("tc", v, "timeout", d.timeout) ? T("", !0) : (i(), u("button", {
                    key: 3,
                    class: "meta-toggle-btn mt-config",
                    onClick: (l) => H("tc", v, "timeout")
                  }, "+ Timeout", 8, Bg)),
                  R("tc", v, "template", d.template) ? T("", !0) : (i(), u("button", {
                    key: 4,
                    class: "meta-toggle-btn mt-config",
                    onClick: (l) => H("tc", v, "template")
                  }, "+ Template", 8, Kg))
                ]),
                R("tc", v, "setup", d.setup) ? (i(), u("div", Hg, [
                  t("label", Og, [
                    we(m(p(a)("robotEditor.setup")) + " ", 1),
                    d.setup ? T("", !0) : (i(), u("button", {
                      key: 0,
                      class: "meta-close-btn",
                      onClick: (l) => H("tc", v, "setup")
                    }, "×", 8, Ig))
                  ]),
                  t("div", Wg, [
                    t("div", jg, [
                      t("input", {
                        value: ie("tc", v, "setup", d).keyword,
                        onInput: (l) => {
                          ie("tc", v, "setup", d).keyword = l.target.value, tt("tc", v, "setup", d);
                        },
                        class: "form-input step-keyword-input",
                        spellcheck: "false",
                        style: Ee({ width: Math.max(18, Math.min((ie("tc", v, "setup", d).keyword || "").length + 5, 40)) + "ch" }),
                        placeholder: p(a)("robotEditor.setupPlaceholder"),
                        onFocus: (l) => gt("tc", v, "setup", d),
                        onBlur: pt,
                        onKeydown: (l) => Tt(l, ie("tc", v, "setup", d))
                      }, null, 44, qg),
                      Y.value === ie("tc", v, "setup", d) && De.value.length > 0 ? (i(), u("div", {
                        key: 0,
                        ref_for: !0,
                        ref_key: "keywordDropdownRef",
                        ref: ce,
                        class: "keyword-dropdown"
                      }, [
                        (i(!0), u(x, null, X(De.value, (l, S) => {
                          var b;
                          return i(), u("div", {
                            key: l.name,
                            class: ne(["keyword-dropdown-item", { active: S === Z.value }]),
                            onMousedown: re((o) => ut("tc", v, "setup", d, l), ["prevent"])
                          }, [
                            t("span", Gg, m(l.name), 1),
                            (b = l.args) != null && b.length ? (i(), u("span", Yg, m(l.args.map((o) => o.replace(/^[$@&%]\{([^}]+)\}.*$/, "$1")).join(", ")), 1)) : T("", !0)
                          ], 42, zg);
                        }), 128))
                      ], 512)) : T("", !0)
                    ]),
                    ie("tc", v, "setup", d).args.length ? (i(), u("div", Xg, [
                      (i(!0), u(x, null, X(ie("tc", v, "setup", d).args, (l, S) => (i(), u("span", {
                        key: S,
                        class: "step-arg-chip"
                      }, [
                        $e(ie("tc", v, "setup", d))[S] ? (i(), u("span", Zg, m($e(ie("tc", v, "setup", d))[S]), 1)) : T("", !0),
                        t("span", {
                          class: "arg-highlight-wrap",
                          style: Ee({ width: lt(l) })
                        }, [
                          t("span", {
                            class: "arg-highlight-overlay",
                            innerHTML: Te(l)
                          }, null, 8, Qg),
                          t("input", {
                            value: l,
                            onInput: (b) => {
                              ie("tc", v, "setup", d).args[S] = b.target.value, ze("tc", v, "setup", d);
                            },
                            class: "step-arg-input",
                            spellcheck: "false",
                            style: { width: "100%" },
                            placeholder: $e(ie("tc", v, "setup", d))[S] || p(a)("robotEditor.argPlaceholder")
                          }, null, 40, Jg)
                        ], 4),
                        t("button", {
                          class: "chip-remove",
                          onClick: (b) => vt("tc", v, "setup", d, S)
                        }, "×", 8, ev)
                      ]))), 128))
                    ])) : T("", !0),
                    t("button", {
                      class: "step-btn step-add-arg",
                      onClick: (l) => Ze("tc", v, "setup", d),
                      title: p(a)("robotEditor.addArg")
                    }, "+", 8, tv)
                  ])
                ])) : T("", !0),
                R("tc", v, "teardown", d.teardown) ? (i(), u("div", ov, [
                  t("label", nv, [
                    we(m(p(a)("robotEditor.teardown")) + " ", 1),
                    d.teardown ? T("", !0) : (i(), u("button", {
                      key: 0,
                      class: "meta-close-btn",
                      onClick: (l) => H("tc", v, "teardown")
                    }, "×", 8, av))
                  ]),
                  t("div", sv, [
                    t("div", lv, [
                      t("input", {
                        value: ie("tc", v, "teardown", d).keyword,
                        onInput: (l) => {
                          ie("tc", v, "teardown", d).keyword = l.target.value, tt("tc", v, "teardown", d);
                        },
                        class: "form-input step-keyword-input",
                        spellcheck: "false",
                        style: Ee({ width: Math.max(18, Math.min((ie("tc", v, "teardown", d).keyword || "").length + 5, 40)) + "ch" }),
                        placeholder: p(a)("robotEditor.teardownPlaceholder"),
                        onFocus: (l) => gt("tc", v, "teardown", d),
                        onBlur: pt,
                        onKeydown: (l) => Tt(l, ie("tc", v, "teardown", d))
                      }, null, 44, rv),
                      Y.value === ie("tc", v, "teardown", d) && De.value.length > 0 ? (i(), u("div", {
                        key: 0,
                        ref_for: !0,
                        ref_key: "keywordDropdownRef",
                        ref: ce,
                        class: "keyword-dropdown"
                      }, [
                        (i(!0), u(x, null, X(De.value, (l, S) => {
                          var b;
                          return i(), u("div", {
                            key: l.name,
                            class: ne(["keyword-dropdown-item", { active: S === Z.value }]),
                            onMousedown: re((o) => ut("tc", v, "teardown", d, l), ["prevent"])
                          }, [
                            t("span", uv, m(l.name), 1),
                            (b = l.args) != null && b.length ? (i(), u("span", cv, m(l.args.map((o) => o.replace(/^[$@&%]\{([^}]+)\}.*$/, "$1")).join(", ")), 1)) : T("", !0)
                          ], 42, iv);
                        }), 128))
                      ], 512)) : T("", !0)
                    ]),
                    ie("tc", v, "teardown", d).args.length ? (i(), u("div", dv, [
                      (i(!0), u(x, null, X(ie("tc", v, "teardown", d).args, (l, S) => (i(), u("span", {
                        key: S,
                        class: "step-arg-chip"
                      }, [
                        $e(ie("tc", v, "teardown", d))[S] ? (i(), u("span", pv, m($e(ie("tc", v, "teardown", d))[S]), 1)) : T("", !0),
                        t("span", {
                          class: "arg-highlight-wrap",
                          style: Ee({ width: lt(l) })
                        }, [
                          t("span", {
                            class: "arg-highlight-overlay",
                            innerHTML: Te(l)
                          }, null, 8, fv),
                          t("input", {
                            value: l,
                            onInput: (b) => {
                              ie("tc", v, "teardown", d).args[S] = b.target.value, ze("tc", v, "teardown", d);
                            },
                            class: "step-arg-input",
                            spellcheck: "false",
                            style: { width: "100%" },
                            placeholder: $e(ie("tc", v, "teardown", d))[S] || p(a)("robotEditor.argPlaceholder")
                          }, null, 40, mv)
                        ], 4),
                        t("button", {
                          class: "chip-remove",
                          onClick: (b) => vt("tc", v, "teardown", d, S)
                        }, "×", 8, gv)
                      ]))), 128))
                    ])) : T("", !0),
                    t("button", {
                      class: "step-btn step-add-arg",
                      onClick: (l) => Ze("tc", v, "teardown", d),
                      title: p(a)("robotEditor.addArg")
                    }, "+", 8, vv)
                  ])
                ])) : T("", !0),
                R("tc", v, "timeout", d.timeout) || R("tc", v, "template", d.template) ? (i(), u("div", hv, [
                  R("tc", v, "timeout", d.timeout) ? (i(), u("div", yv, [
                    t("label", bv, [
                      we(m(p(a)("robotEditor.timeout")) + " ", 1),
                      d.timeout ? T("", !0) : (i(), u("button", {
                        key: 0,
                        class: "meta-close-btn",
                        onClick: (l) => H("tc", v, "timeout")
                      }, "×", 8, wv))
                    ]),
                    t("span", _v, [
                      t("span", {
                        class: "form-hl-overlay",
                        innerHTML: Te(d.timeout)
                      }, null, 8, kv),
                      q(t("input", {
                        "onUpdate:modelValue": (l) => d.timeout = l,
                        class: "form-input",
                        style: { width: "100%" },
                        placeholder: p(a)("robotEditor.timeoutPlaceholder"),
                        spellcheck: "false"
                      }, null, 8, Cv), [
                        [le, d.timeout]
                      ])
                    ])
                  ])) : T("", !0),
                  R("tc", v, "template", d.template) ? (i(), u("div", $v, [
                    t("label", Tv, [
                      we(m(p(a)("robotEditor.template")) + " ", 1),
                      d.template ? T("", !0) : (i(), u("button", {
                        key: 0,
                        class: "meta-close-btn",
                        onClick: (l) => H("tc", v, "template")
                      }, "×", 8, Ev))
                    ]),
                    t("span", Sv, [
                      t("span", {
                        class: "form-hl-overlay",
                        innerHTML: Te(d.template)
                      }, null, 8, Lv),
                      q(t("input", {
                        "onUpdate:modelValue": (l) => d.template = l,
                        class: "form-input",
                        style: { width: "100%" },
                        placeholder: p(a)("robotEditor.templatePlaceholder"),
                        spellcheck: "false"
                      }, null, 8, Vv), [
                        [le, d.template]
                      ])
                    ])
                  ])) : T("", !0)
                ])) : T("", !0),
                t("div", Av, [
                  t("label", Nv, m(p(a)("robotEditor.steps")), 1),
                  t("div", Rv, [
                    (i(!0), u(x, null, X(d.steps, (l, S) => (i(), u("div", {
                      key: S,
                      class: "step-row",
                      style: Ee({ paddingLeft: st(d.steps, S) * 24 + "px" })
                    }, [
                      t("span", Fv, m(S + 1) + ".", 1),
                      q(t("select", {
                        "onUpdate:modelValue": (b) => l.type = b,
                        class: ne(["step-type-select", Ro(l.type)]),
                        onChange: (b) => vo(l)
                      }, [
                        (i(!0), u(x, null, X(p(Ta), (b) => (i(), u("option", {
                          key: b,
                          value: b
                        }, m(en(b)), 9, Dv))), 128))
                      ], 42, Pv), [
                        [it, l.type]
                      ]),
                      l.type === "keyword" ? (i(), u(x, { key: 0 }, [
                        t("div", Mv, [
                          q(t("input", {
                            "onUpdate:modelValue": (b) => l.keyword = b,
                            class: "form-input step-keyword-input",
                            spellcheck: "false",
                            style: Ee({ width: Math.max(18, Math.min((l.keyword || "").length + 5, 60)) + "ch" }),
                            placeholder: p(a)("robotEditor.keywordPlaceholder"),
                            onFocus: (b) => qt(l),
                            onBlur: pt,
                            onKeydown: (b) => Tt(b, l),
                            onInput: (b) => zt(l)
                          }, null, 44, xv), [
                            [le, l.keyword]
                          ]),
                          Y.value === l && De.value.length > 0 ? (i(), u("div", {
                            key: 0,
                            ref_for: !0,
                            ref_key: "keywordDropdownRef",
                            ref: ce,
                            class: "keyword-dropdown"
                          }, [
                            (i(!0), u(x, null, X(De.value, (b, o) => {
                              var s;
                              return i(), u("div", {
                                key: b.name,
                                class: ne(["keyword-dropdown-item", { active: o === Z.value }]),
                                onMousedown: re(($) => Gt(l, b), ["prevent"])
                              }, [
                                t("span", Bv, m(b.name), 1),
                                (s = b.args) != null && s.length ? (i(), u("span", Kv, m(b.args.map(($) => $.replace(/^[$@&%]\{([^}]+)\}.*$/, "$1")).join(", ")), 1)) : T("", !0),
                                t("span", {
                                  class: ne(["kw-suggestion-source", "source-" + (b.source === "builtin" ? "builtin" : b.source === "local" || b.source === "project" ? "local" : "library")])
                                }, m(b.source === "builtin" ? p(a)("robotEditor.builtinKeyword") : b.source === "local" || b.source === "project" ? p(a)("robotEditor.localKeyword") : b.source), 3)
                              ], 42, Uv);
                            }), 128))
                          ], 512)) : T("", !0)
                        ]),
                        l.args.length ? (i(), u("div", Hv, [
                          (i(!0), u(x, null, X(l.args, (b, o) => (i(), u("span", {
                            key: o,
                            class: "step-arg-chip arg-ac-wrapper"
                          }, [
                            $e(l)[o] ? (i(), u("span", Ov, m($e(l)[o]), 1)) : T("", !0),
                            t("span", {
                              class: "arg-highlight-wrap",
                              style: Ee({ width: lt(l.args[o]) })
                            }, [
                              t("span", {
                                class: "arg-highlight-overlay",
                                innerHTML: Te(l.args[o])
                              }, null, 8, Iv),
                              q(t("input", {
                                "onUpdate:modelValue": (s) => l.args[o] = s,
                                class: "step-arg-input",
                                spellcheck: "false",
                                onScroll: Xt,
                                style: { width: "100%" },
                                placeholder: $e(l)[o] || p(a)("robotEditor.argPlaceholder"),
                                onInput: (s) => Dt(s.target.value, `${S}-${o}`),
                                onKeydown: (s) => Mt(s, l, o),
                                onBlur: ho,
                                autocomplete: "off"
                              }, null, 40, Wv), [
                                [le, l.args[o]]
                              ])
                            ], 4),
                            be.value === `${S}-${o}` && oe.value.length ? (i(), u("div", jv, [
                              (i(!0), u(x, null, X(oe.value, (s, $) => (i(), u("div", {
                                key: s,
                                class: ne(["arg-ac-item", { active: $ === fe.value }]),
                                onMousedown: re((K) => Yt(l, o, s), ["prevent"])
                              }, m(s), 43, qv))), 128))
                            ])) : T("", !0),
                            t("button", {
                              class: "chip-remove",
                              onClick: (s) => Qe(l, o)
                            }, "×", 8, zv)
                          ]))), 128))
                        ])) : T("", !0),
                        t("button", {
                          class: ne(["step-btn step-add-arg", { "has-hint": $t(l) !== "+" }]),
                          onClick: (b) => dt(l),
                          title: p(a)("robotEditor.addArg")
                        }, m($t(l)), 11, Gv)
                      ], 64)) : l.type === "assignment" ? (i(), u(x, { key: 1 }, [
                        (i(!0), u(x, null, X(l.returnVars, (b, o) => (i(), u("span", {
                          key: o,
                          class: "step-var-chip"
                        }, [
                          q(t("input", {
                            "onUpdate:modelValue": (s) => l.returnVars[o] = s,
                            class: "step-var-input",
                            spellcheck: "false",
                            placeholder: "${var}",
                            onBlur: (s) => yo(l, o)
                          }, null, 40, Yv), [
                            [le, l.returnVars[o]]
                          ]),
                          t("button", {
                            class: "chip-remove",
                            onClick: (s) => Jo(l, o)
                          }, "×", 8, Xv)
                        ]))), 128)),
                        t("button", {
                          class: "step-btn step-add-var",
                          onClick: (b) => at(l),
                          title: "+var"
                        }, "+v", 8, Zv),
                        h[11] || (h[11] = t("span", { class: "step-assign-eq" }, "=", -1)),
                        t("div", Qv, [
                          q(t("input", {
                            "onUpdate:modelValue": (b) => l.keyword = b,
                            class: "form-input step-keyword-input",
                            spellcheck: "false",
                            style: Ee({ width: Math.max(18, Math.min((l.keyword || "").length + 5, 60)) + "ch" }),
                            placeholder: p(a)("robotEditor.keywordPlaceholder"),
                            onFocus: (b) => qt(l),
                            onBlur: pt,
                            onKeydown: (b) => Tt(b, l),
                            onInput: (b) => zt(l)
                          }, null, 44, Jv), [
                            [le, l.keyword]
                          ]),
                          Y.value === l && De.value.length > 0 ? (i(), u("div", {
                            key: 0,
                            ref_for: !0,
                            ref_key: "keywordDropdownRef",
                            ref: ce,
                            class: "keyword-dropdown"
                          }, [
                            (i(!0), u(x, null, X(De.value, (b, o) => {
                              var s;
                              return i(), u("div", {
                                key: b.name,
                                class: ne(["keyword-dropdown-item", { active: o === Z.value }]),
                                onMousedown: re(($) => Gt(l, b), ["prevent"])
                              }, [
                                t("span", th, m(b.name), 1),
                                (s = b.args) != null && s.length ? (i(), u("span", oh, m(b.args.map(($) => $.replace(/^[$@&%]\{([^}]+)\}.*$/, "$1")).join(", ")), 1)) : T("", !0),
                                t("span", {
                                  class: ne(["kw-suggestion-source", "source-" + (b.source === "builtin" ? "builtin" : b.source === "local" || b.source === "project" ? "local" : "library")])
                                }, m(b.source === "builtin" ? p(a)("robotEditor.builtinKeyword") : b.source === "local" || b.source === "project" ? p(a)("robotEditor.localKeyword") : b.source), 3)
                              ], 42, eh);
                            }), 128))
                          ], 512)) : T("", !0)
                        ]),
                        l.args.length ? (i(), u("div", nh, [
                          (i(!0), u(x, null, X(l.args, (b, o) => (i(), u("span", {
                            key: o,
                            class: "step-arg-chip arg-ac-wrapper"
                          }, [
                            $e(l)[o] ? (i(), u("span", ah, m($e(l)[o]), 1)) : T("", !0),
                            t("span", {
                              class: "arg-highlight-wrap",
                              style: Ee({ width: lt(l.args[o]) })
                            }, [
                              t("span", {
                                class: "arg-highlight-overlay",
                                innerHTML: Te(l.args[o])
                              }, null, 8, sh),
                              q(t("input", {
                                "onUpdate:modelValue": (s) => l.args[o] = s,
                                class: "step-arg-input",
                                spellcheck: "false",
                                onScroll: Xt,
                                style: { width: "100%" },
                                placeholder: $e(l)[o] || p(a)("robotEditor.argPlaceholder"),
                                onInput: (s) => Dt(s.target.value, `${S}-${o}`),
                                onKeydown: (s) => Mt(s, l, o),
                                onBlur: ho,
                                autocomplete: "off"
                              }, null, 40, lh), [
                                [le, l.args[o]]
                              ])
                            ], 4),
                            be.value === `${S}-${o}` && oe.value.length ? (i(), u("div", rh, [
                              (i(!0), u(x, null, X(oe.value, (s, $) => (i(), u("div", {
                                key: s,
                                class: ne(["arg-ac-item", { active: $ === fe.value }]),
                                onMousedown: re((K) => Yt(l, o, s), ["prevent"])
                              }, m(s), 43, ih))), 128))
                            ])) : T("", !0),
                            t("button", {
                              class: "chip-remove",
                              onClick: (s) => Qe(l, o)
                            }, "×", 8, uh)
                          ]))), 128))
                        ])) : T("", !0),
                        t("button", {
                          class: ne(["step-btn step-add-arg", { "has-hint": $t(l) !== "+" }]),
                          onClick: (b) => dt(l),
                          title: p(a)("robotEditor.addArg")
                        }, m($t(l)), 11, ch)
                      ], 64)) : l.type === "var" ? (i(), u(x, { key: 2 }, [
                        t("span", dh, [
                          q(t("input", {
                            "onUpdate:modelValue": (b) => l.returnVars[0] = b,
                            class: "step-var-input",
                            spellcheck: "false",
                            placeholder: "${var}",
                            onBlur: (b) => yo(l, 0)
                          }, null, 40, ph), [
                            [le, l.returnVars[0]]
                          ])
                        ]),
                        h[13] || (h[13] = t("span", { class: "step-assign-eq" }, "=", -1)),
                        (i(!0), u(x, null, X(l.args, (b, o) => (i(), u("span", {
                          key: o,
                          class: "step-arg-chip"
                        }, [
                          t("span", {
                            class: "arg-highlight-wrap",
                            style: Ee({ width: lt(l.args[o]) })
                          }, [
                            t("span", {
                              class: "arg-highlight-overlay",
                              innerHTML: Te(l.args[o])
                            }, null, 8, fh),
                            q(t("input", {
                              "onUpdate:modelValue": (s) => l.args[o] = s,
                              class: "step-arg-input",
                              spellcheck: "false",
                              onScroll: Xt,
                              style: { width: "100%" },
                              placeholder: p(a)("robotEditor.valuePlaceholder")
                            }, null, 40, mh), [
                              [le, l.args[o]]
                            ])
                          ], 4),
                          t("button", {
                            class: "chip-remove",
                            onClick: (s) => Qe(l, o)
                          }, "×", 8, gh)
                        ]))), 128)),
                        t("button", {
                          class: "step-btn step-add-arg",
                          onClick: (b) => dt(l),
                          title: p(a)("robotEditor.addValue")
                        }, "+", 8, vh),
                        q(t("select", {
                          "onUpdate:modelValue": (b) => l.varScope = b,
                          class: "step-scope-select"
                        }, [
                          h[12] || (h[12] = t("option", { value: "" }, "scope", -1)),
                          (i(!0), u(x, null, X(p(Ea), (b) => (i(), u("option", {
                            key: b,
                            value: b
                          }, m(b), 9, yh))), 128))
                        ], 8, hh), [
                          [it, l.varScope]
                        ])
                      ], 64)) : l.type === "for" ? (i(), u(x, { key: 3 }, [
                        t("span", bh, [
                          t("span", {
                            class: "form-hl-overlay hl-mono",
                            innerHTML: Te(l.loopVar)
                          }, null, 8, wh),
                          q(t("input", {
                            "onUpdate:modelValue": (b) => l.loopVar = b,
                            class: "form-input step-var-input-inline",
                            spellcheck: "false",
                            style: { width: "100%" },
                            placeholder: "${item}"
                          }, null, 8, _h), [
                            [le, l.loopVar]
                          ])
                        ]),
                        q(t("select", {
                          "onUpdate:modelValue": (b) => l.loopFlavor = b,
                          class: "step-flavor-select"
                        }, [
                          (i(!0), u(x, null, X(p(ea), (b) => (i(), u("option", {
                            key: b,
                            value: b
                          }, m(b), 9, Ch))), 128))
                        ], 8, kh), [
                          [it, l.loopFlavor]
                        ]),
                        (i(!0), u(x, null, X(l.loopValues, (b, o) => (i(), u("span", {
                          key: o,
                          class: "step-arg-chip"
                        }, [
                          q(t("input", {
                            "onUpdate:modelValue": (s) => l.loopValues[o] = s,
                            class: "step-arg-input",
                            placeholder: p(a)("robotEditor.valuePlaceholder")
                          }, null, 8, $h), [
                            [le, l.loopValues[o]]
                          ]),
                          t("button", {
                            class: "chip-remove",
                            onClick: (s) => No(l, o)
                          }, "×", 8, Th)
                        ]))), 128)),
                        t("button", {
                          class: "step-btn step-add-arg",
                          onClick: (b) => ao(l),
                          title: p(a)("robotEditor.addValue")
                        }, "+", 8, Eh)
                      ], 64)) : l.type === "if" || l.type === "else_if" || l.type === "while" ? (i(), u("span", Sh, [
                        t("span", {
                          class: "form-hl-overlay hl-condition",
                          innerHTML: Te(l.condition)
                        }, null, 8, Lh),
                        q(t("input", {
                          "onUpdate:modelValue": (b) => l.condition = b,
                          class: "form-input step-condition-input",
                          style: { width: "100%" },
                          placeholder: p(a)("robotEditor.conditionPlaceholder"),
                          spellcheck: "false"
                        }, null, 8, Vh), [
                          [le, l.condition]
                        ])
                      ])) : l.type === "except" ? (i(), u(x, { key: 5 }, [
                        q(t("input", {
                          "onUpdate:modelValue": (b) => l.exceptPattern = b,
                          class: "form-input step-keyword-input",
                          spellcheck: "false",
                          placeholder: p(a)("robotEditor.exceptPatternPlaceholder")
                        }, null, 8, Ah), [
                          [le, l.exceptPattern]
                        ]),
                        l.exceptVar || l.exceptPattern ? (i(), u("span", Nh, "AS")) : T("", !0),
                        l.exceptVar || l.exceptPattern ? (i(), u("span", Rh, [
                          t("span", {
                            class: "form-hl-overlay hl-mono",
                            innerHTML: Te(l.exceptVar)
                          }, null, 8, Fh),
                          q(t("input", {
                            "onUpdate:modelValue": (b) => l.exceptVar = b,
                            class: "form-input step-var-input-inline",
                            spellcheck: "false",
                            style: { width: "100%" },
                            placeholder: "${error}"
                          }, null, 8, Ph), [
                            [le, l.exceptVar]
                          ])
                        ])) : T("", !0)
                      ], 64)) : l.type === "return" ? (i(), u(x, { key: 6 }, [
                        (i(!0), u(x, null, X(l.args, (b, o) => (i(), u("span", {
                          key: o,
                          class: "step-arg-chip"
                        }, [
                          q(t("input", {
                            "onUpdate:modelValue": (s) => l.args[o] = s,
                            class: "step-arg-input",
                            placeholder: p(a)("robotEditor.valuePlaceholder")
                          }, null, 8, Dh), [
                            [le, l.args[o]]
                          ]),
                          t("button", {
                            class: "chip-remove",
                            onClick: (s) => Qe(l, o)
                          }, "×", 8, Mh)
                        ]))), 128)),
                        t("button", {
                          class: "step-btn step-add-arg",
                          onClick: (b) => dt(l),
                          title: p(a)("robotEditor.addValue")
                        }, "+", 8, xh)
                      ], 64)) : l.type === "comment" ? q((i(), u("input", {
                        key: 7,
                        "onUpdate:modelValue": (b) => l.comment = b,
                        class: "form-input flex-1 step-comment-input",
                        spellcheck: "false",
                        placeholder: "# ..."
                      }, null, 8, Uh)), [
                        [le, l.comment]
                      ]) : T("", !0),
                      t("div", Bh, [
                        t("button", {
                          class: "step-btn",
                          onClick: (b) => go(d.steps, S, -1),
                          disabled: S === 0
                        }, "↑", 8, Kh),
                        t("button", {
                          class: "step-btn",
                          onClick: (b) => go(d.steps, S, 1),
                          disabled: S === d.steps.length - 1
                        }, "↓", 8, Hh),
                        t("button", {
                          class: "step-btn danger",
                          onClick: (b) => mo(d.steps, S),
                          title: p(a)("editor.common.delete")
                        }, "×", 8, Oh)
                      ])
                    ], 4))), 128)),
                    t("div", Ih, [
                      t("button", {
                        class: "add-step-btn add-keyword",
                        onClick: (l) => no(d.steps)
                      }, "+ " + m(p(a)("robotEditor.addStep")), 9, Wh),
                      t("button", {
                        class: "add-step-btn add-block add-var",
                        onClick: (l) => no(d.steps, "var")
                      }, "+ VAR", 8, jh),
                      t("button", {
                        class: "add-step-btn add-block add-loop",
                        onClick: (l) => Ct(d.steps, "for")
                      }, "+ FOR", 8, qh),
                      t("button", {
                        class: "add-step-btn add-block add-condition",
                        onClick: (l) => Ct(d.steps, "if")
                      }, "+ IF", 8, zh),
                      t("button", {
                        class: "add-step-btn add-block add-loop",
                        onClick: (l) => Ct(d.steps, "while")
                      }, "+ WHILE", 8, Gh),
                      t("button", {
                        class: "add-step-btn add-block add-error",
                        onClick: (l) => Ct(d.steps, "try")
                      }, "+ TRY", 8, Yh)
                    ])
                  ])
                ])
              ], 512), [
                [eo, !M.value.has(v)]
              ])
            ]))), 128))
          ], 512), [
            [eo, !j.value]
          ])
        ])),
        t("div", Xh, [
          t("div", {
            class: "section-header",
            onClick: h[6] || (h[6] = (d) => ue.value = !ue.value)
          }, [
            t("span", Zh, m(ue.value ? "▶" : "▼"), 1),
            t("h3", null, m(p(a)("robotEditor.keywordsSection")), 1),
            t("span", Qh, m(Q.keywords.length), 1),
            ve(Oe, {
              size: "sm",
              variant: "secondary",
              onClick: re(Zt, ["stop"])
            }, {
              default: _e(() => [
                we("+ " + m(p(a)("robotEditor.addKeyword")), 1)
              ]),
              _: 1
            })
          ]),
          q(t("div", Jh, [
            Q.keywords.length === 0 ? (i(), u("div", ey, m(p(a)("robotEditor.noKeywords")), 1)) : T("", !0),
            (i(!0), u(x, null, X(Q.keywords, (d, v) => (i(), u("div", {
              key: v,
              class: "item-card"
            }, [
              t("div", {
                class: "item-header",
                onClick: (l) => rn(v)
              }, [
                t("span", oy, m(J.value.has(v) ? "▶" : "▼"), 1),
                t("span", ny, m(d.name || p(a)("robotEditor.unnamedKeyword")), 1),
                t("span", ay, m(d.steps.length) + " " + m(p(a)("robotEditor.steps")), 1),
                t("button", {
                  class: "remove-btn",
                  onClick: re((l) => ln(v), ["stop"]),
                  title: p(a)("editor.common.delete")
                }, "🗑", 8, sy)
              ], 8, ty),
              q(t("div", ly, [
                t("div", ry, [
                  t("label", iy, [
                    we(m(p(a)("robotEditor.keywordName")) + " ", 1),
                    h[14] || (h[14] = t("span", { class: "required" }, "*", -1))
                  ]),
                  q(t("input", {
                    "onUpdate:modelValue": (l) => d.name = l,
                    class: "form-input",
                    placeholder: p(a)("robotEditor.keywordNamePlaceholder")
                  }, null, 8, uy), [
                    [le, d.name]
                  ])
                ]),
                t("div", cy, [
                  t("label", dy, m(p(a)("robotEditor.arguments")), 1),
                  t("div", py, [
                    (i(!0), u(x, null, X(d.arguments, (l, S) => (i(), u("div", {
                      key: S,
                      class: ne(["kw-arg-row", "kw-arg-" + (l === "@{}" ? "sep" : xo(d, S) ? "named" : Hn(l))])
                    }, [
                      l === "@{}" ? (i(), u(x, { key: 0 }, [
                        h[15] || (h[15] = t("span", { class: "kw-arg-sep-line" }, null, -1)),
                        t("span", fy, m(p(a)("robotEditor.argTypes.namedOnlyBelow")), 1),
                        h[16] || (h[16] = t("span", { class: "kw-arg-sep-line" }, null, -1))
                      ], 64)) : l.startsWith("@{") ? (i(), u(x, { key: 1 }, [
                        t("span", my, "*" + m(p(a)("robotEditor.argTypes.varargs")), 1),
                        t("input", {
                          class: "kw-arg-name-input",
                          spellcheck: "false",
                          value: Mo(l),
                          onInput: (b) => wo(d, S, b.target.value),
                          onBlur: (b) => Fo(d, S),
                          placeholder: "args"
                        }, null, 40, gy)
                      ], 64)) : l.startsWith("&{") ? (i(), u(x, { key: 2 }, [
                        t("span", vy, "**" + m(p(a)("robotEditor.argTypes.kwargs")), 1),
                        t("input", {
                          class: "kw-arg-name-input",
                          spellcheck: "false",
                          value: Mo(l),
                          onInput: (b) => wo(d, S, b.target.value),
                          onBlur: (b) => Fo(d, S),
                          placeholder: "kwargs"
                        }, null, 40, hy)
                      ], 64)) : (i(), u(x, { key: 3 }, [
                        xo(d, S) ? (i(), u("span", yy, m(p(a)("robotEditor.argTypes.namedOnly")), 1)) : l.includes("=") ? (i(), u("span", by, m(p(a)("robotEditor.argTypes.optional")), 1)) : (i(), u("span", wy, m(p(a)("robotEditor.argTypes.required")), 1)),
                        t("input", {
                          class: "kw-arg-name-input",
                          spellcheck: "false",
                          value: Mo(l),
                          onInput: (b) => wo(d, S, b.target.value),
                          onBlur: (b) => Fo(d, S),
                          placeholder: p(a)("robotEditor.argTypes.namePlaceholder")
                        }, null, 40, _y),
                        l.includes("=") ? (i(), u(x, { key: 3 }, [
                          h[17] || (h[17] = t("span", { class: "kw-arg-eq" }, "=", -1)),
                          t("span", ky, [
                            t("span", {
                              class: "form-hl-overlay hl-kw-default",
                              innerHTML: Te(un(l))
                            }, null, 8, Cy),
                            t("input", {
                              class: "kw-arg-default-input",
                              spellcheck: "false",
                              value: un(l),
                              onInput: (b) => cn(d, S, b.target.value),
                              placeholder: p(a)("robotEditor.argTypes.defaultPlaceholder")
                            }, null, 40, $y)
                          ])
                        ], 64)) : T("", !0),
                        l.includes("=") ? T("", !0) : (i(), u("button", {
                          key: 4,
                          class: "kw-arg-opt-btn",
                          onClick: (b) => d.arguments[S] = l + "=",
                          title: p(a)("robotEditor.argTypes.addDefault")
                        }, "= ?", 8, Ty))
                      ], 64)),
                      t("button", {
                        class: "chip-remove",
                        onClick: (b) => Do(v, S)
                      }, "×", 8, Ey)
                    ], 2))), 128))
                  ]),
                  t("div", Sy, [
                    t("button", {
                      class: "kw-arg-add-btn",
                      onClick: (l) => xt(v, "positional")
                    }, "+ " + m(p(a)("robotEditor.argTypes.required")), 9, Ly),
                    t("button", {
                      class: "kw-arg-add-btn",
                      onClick: (l) => xt(v, "optional")
                    }, "+ " + m(p(a)("robotEditor.argTypes.optional")), 9, Vy),
                    t("button", {
                      class: "kw-arg-add-btn",
                      disabled: d.arguments.some((l) => l.startsWith("@{")),
                      onClick: (l) => xt(v, "varargs")
                    }, "+ " + m(p(a)("robotEditor.argTypes.varargs")), 9, Ay),
                    t("button", {
                      class: "kw-arg-add-btn",
                      disabled: d.arguments.some((l) => l.startsWith("&{")),
                      onClick: (l) => xt(v, "kwargs")
                    }, "+ " + m(p(a)("robotEditor.argTypes.kwargs")), 9, Ny),
                    t("button", {
                      class: "kw-arg-add-btn",
                      onClick: (l) => xt(v, "named-only")
                    }, "+ " + m(p(a)("robotEditor.argTypes.namedOnly")), 9, Ry)
                  ])
                ]),
                t("div", Fy, [
                  t("label", Py, m(p(a)("robotEditor.documentation")), 1),
                  q(t("textarea", {
                    "onUpdate:modelValue": (l) => d.documentation = l,
                    class: "form-input form-textarea",
                    rows: "2",
                    placeholder: p(a)("robotEditor.documentationPlaceholder")
                  }, null, 8, Dy), [
                    [le, d.documentation]
                  ])
                ]),
                R("kw", v, "tags", d.tags.length ? "x" : "") ? (i(), u("div", My, [
                  t("label", xy, [
                    we(m(p(a)("robotEditor.tags")) + " ", 1),
                    d.tags.length ? T("", !0) : (i(), u("button", {
                      key: 0,
                      class: "meta-close-btn",
                      onClick: (l) => H("kw", v, "tags")
                    }, "×", 8, Uy))
                  ]),
                  t("div", By, [
                    (i(!0), u(x, null, X(d.tags, (l, S) => (i(), u("span", {
                      key: S,
                      class: "chip chip-tag"
                    }, [
                      we(m(l) + " ", 1),
                      t("button", {
                        class: "chip-remove",
                        onClick: (b) => Qt(v, S)
                      }, "×", 8, Ky)
                    ]))), 128)),
                    t("div", Hy, [
                      t("input", {
                        value: so.value.get(v) || "",
                        onInput: (l) => so.value.set(v, l.target.value),
                        class: "chip-input",
                        placeholder: p(a)("robotEditor.addTag"),
                        onKeydown: Ye(re((l) => dn(v), ["prevent"]), ["enter"])
                      }, null, 40, Oy),
                      (so.value.get(v) || "").trim() ? (i(), u("button", {
                        key: 0,
                        class: "chip-add-btn",
                        onClick: (l) => dn(v)
                      }, "+", 8, Iy)) : T("", !0)
                    ])
                  ])
                ])) : T("", !0),
                t("div", Wy, [
                  R("kw", v, "tags", d.tags.length ? "x" : "") ? T("", !0) : (i(), u("button", {
                    key: 0,
                    class: "meta-toggle-btn mt-tags",
                    onClick: (l) => H("kw", v, "tags")
                  }, "+ Tags", 8, jy)),
                  R("kw", v, "setup", d.setup) ? T("", !0) : (i(), u("button", {
                    key: 1,
                    class: "meta-toggle-btn mt-setup",
                    onClick: (l) => H("kw", v, "setup")
                  }, "+ Setup", 8, qy)),
                  R("kw", v, "teardown", d.teardown) ? T("", !0) : (i(), u("button", {
                    key: 2,
                    class: "meta-toggle-btn mt-teardown",
                    onClick: (l) => H("kw", v, "teardown")
                  }, "+ Teardown", 8, zy)),
                  R("kw", v, "timeout", d.timeout) ? T("", !0) : (i(), u("button", {
                    key: 3,
                    class: "meta-toggle-btn mt-config",
                    onClick: (l) => H("kw", v, "timeout")
                  }, "+ Timeout", 8, Gy)),
                  R("kw", v, "return", d.returnValue) ? T("", !0) : (i(), u("button", {
                    key: 4,
                    class: "meta-toggle-btn mt-return",
                    onClick: (l) => H("kw", v, "return")
                  }, "+ Return", 8, Yy))
                ]),
                R("kw", v, "setup", d.setup) ? (i(), u("div", Xy, [
                  t("label", Zy, [
                    we(m(p(a)("robotEditor.setup")) + " ", 1),
                    d.setup ? T("", !0) : (i(), u("button", {
                      key: 0,
                      class: "meta-close-btn",
                      onClick: (l) => H("kw", v, "setup")
                    }, "×", 8, Qy))
                  ]),
                  t("div", Jy, [
                    t("div", eb, [
                      t("input", {
                        value: ie("kw", v, "setup", d).keyword,
                        onInput: (l) => {
                          ie("kw", v, "setup", d).keyword = l.target.value, tt("kw", v, "setup", d);
                        },
                        class: "form-input step-keyword-input",
                        spellcheck: "false",
                        style: Ee({ width: Math.max(18, Math.min((ie("kw", v, "setup", d).keyword || "").length + 5, 40)) + "ch" }),
                        placeholder: p(a)("robotEditor.setupPlaceholder"),
                        onFocus: (l) => gt("kw", v, "setup", d),
                        onBlur: pt,
                        onKeydown: (l) => Tt(l, ie("kw", v, "setup", d))
                      }, null, 44, tb),
                      Y.value === ie("kw", v, "setup", d) && De.value.length > 0 ? (i(), u("div", {
                        key: 0,
                        ref_for: !0,
                        ref_key: "keywordDropdownRef",
                        ref: ce,
                        class: "keyword-dropdown"
                      }, [
                        (i(!0), u(x, null, X(De.value, (l, S) => {
                          var b;
                          return i(), u("div", {
                            key: l.name,
                            class: ne(["keyword-dropdown-item", { active: S === Z.value }]),
                            onMousedown: re((o) => ut("kw", v, "setup", d, l), ["prevent"])
                          }, [
                            t("span", nb, m(l.name), 1),
                            (b = l.args) != null && b.length ? (i(), u("span", ab, m(l.args.map((o) => o.replace(/^[$@&%]\{([^}]+)\}.*$/, "$1")).join(", ")), 1)) : T("", !0)
                          ], 42, ob);
                        }), 128))
                      ], 512)) : T("", !0)
                    ]),
                    ie("kw", v, "setup", d).args.length ? (i(), u("div", sb, [
                      (i(!0), u(x, null, X(ie("kw", v, "setup", d).args, (l, S) => (i(), u("span", {
                        key: S,
                        class: "step-arg-chip"
                      }, [
                        $e(ie("kw", v, "setup", d))[S] ? (i(), u("span", lb, m($e(ie("kw", v, "setup", d))[S]), 1)) : T("", !0),
                        t("span", {
                          class: "arg-highlight-wrap",
                          style: Ee({ width: lt(l) })
                        }, [
                          t("span", {
                            class: "arg-highlight-overlay",
                            innerHTML: Te(l)
                          }, null, 8, rb),
                          t("input", {
                            value: l,
                            onInput: (b) => {
                              ie("kw", v, "setup", d).args[S] = b.target.value, ze("kw", v, "setup", d);
                            },
                            class: "step-arg-input",
                            spellcheck: "false",
                            style: { width: "100%" },
                            placeholder: $e(ie("kw", v, "setup", d))[S] || p(a)("robotEditor.argPlaceholder")
                          }, null, 40, ib)
                        ], 4),
                        t("button", {
                          class: "chip-remove",
                          onClick: (b) => vt("kw", v, "setup", d, S)
                        }, "×", 8, ub)
                      ]))), 128))
                    ])) : T("", !0),
                    t("button", {
                      class: "step-btn step-add-arg",
                      onClick: (l) => Ze("kw", v, "setup", d),
                      title: p(a)("robotEditor.addArg")
                    }, "+", 8, cb)
                  ])
                ])) : T("", !0),
                R("kw", v, "teardown", d.teardown) ? (i(), u("div", db, [
                  t("label", pb, [
                    we(m(p(a)("robotEditor.teardown")) + " ", 1),
                    d.teardown ? T("", !0) : (i(), u("button", {
                      key: 0,
                      class: "meta-close-btn",
                      onClick: (l) => H("kw", v, "teardown")
                    }, "×", 8, fb))
                  ]),
                  t("div", mb, [
                    t("div", gb, [
                      t("input", {
                        value: ie("kw", v, "teardown", d).keyword,
                        onInput: (l) => {
                          ie("kw", v, "teardown", d).keyword = l.target.value, tt("kw", v, "teardown", d);
                        },
                        class: "form-input step-keyword-input",
                        spellcheck: "false",
                        style: Ee({ width: Math.max(18, Math.min((ie("kw", v, "teardown", d).keyword || "").length + 5, 40)) + "ch" }),
                        placeholder: p(a)("robotEditor.teardownPlaceholder"),
                        onFocus: (l) => gt("kw", v, "teardown", d),
                        onBlur: pt,
                        onKeydown: (l) => Tt(l, ie("kw", v, "teardown", d))
                      }, null, 44, vb),
                      Y.value === ie("kw", v, "teardown", d) && De.value.length > 0 ? (i(), u("div", {
                        key: 0,
                        ref_for: !0,
                        ref_key: "keywordDropdownRef",
                        ref: ce,
                        class: "keyword-dropdown"
                      }, [
                        (i(!0), u(x, null, X(De.value, (l, S) => {
                          var b;
                          return i(), u("div", {
                            key: l.name,
                            class: ne(["keyword-dropdown-item", { active: S === Z.value }]),
                            onMousedown: re((o) => ut("kw", v, "teardown", d, l), ["prevent"])
                          }, [
                            t("span", yb, m(l.name), 1),
                            (b = l.args) != null && b.length ? (i(), u("span", bb, m(l.args.map((o) => o.replace(/^[$@&%]\{([^}]+)\}.*$/, "$1")).join(", ")), 1)) : T("", !0)
                          ], 42, hb);
                        }), 128))
                      ], 512)) : T("", !0)
                    ]),
                    ie("kw", v, "teardown", d).args.length ? (i(), u("div", wb, [
                      (i(!0), u(x, null, X(ie("kw", v, "teardown", d).args, (l, S) => (i(), u("span", {
                        key: S,
                        class: "step-arg-chip"
                      }, [
                        $e(ie("kw", v, "teardown", d))[S] ? (i(), u("span", _b, m($e(ie("kw", v, "teardown", d))[S]), 1)) : T("", !0),
                        t("span", {
                          class: "arg-highlight-wrap",
                          style: Ee({ width: lt(l) })
                        }, [
                          t("span", {
                            class: "arg-highlight-overlay",
                            innerHTML: Te(l)
                          }, null, 8, kb),
                          t("input", {
                            value: l,
                            onInput: (b) => {
                              ie("kw", v, "teardown", d).args[S] = b.target.value, ze("kw", v, "teardown", d);
                            },
                            class: "step-arg-input",
                            spellcheck: "false",
                            style: { width: "100%" },
                            placeholder: $e(ie("kw", v, "teardown", d))[S] || p(a)("robotEditor.argPlaceholder")
                          }, null, 40, Cb)
                        ], 4),
                        t("button", {
                          class: "chip-remove",
                          onClick: (b) => vt("kw", v, "teardown", d, S)
                        }, "×", 8, $b)
                      ]))), 128))
                    ])) : T("", !0),
                    t("button", {
                      class: "step-btn step-add-arg",
                      onClick: (l) => Ze("kw", v, "teardown", d),
                      title: p(a)("robotEditor.addArg")
                    }, "+", 8, Tb)
                  ])
                ])) : T("", !0),
                R("kw", v, "timeout", d.timeout) || R("kw", v, "return", d.returnValue) ? (i(), u("div", Eb, [
                  R("kw", v, "timeout", d.timeout) ? (i(), u("div", Sb, [
                    t("label", Lb, [
                      we(m(p(a)("robotEditor.timeout")) + " ", 1),
                      d.timeout ? T("", !0) : (i(), u("button", {
                        key: 0,
                        class: "meta-close-btn",
                        onClick: (l) => H("kw", v, "timeout")
                      }, "×", 8, Vb))
                    ]),
                    t("span", Ab, [
                      t("span", {
                        class: "form-hl-overlay",
                        innerHTML: Te(d.timeout)
                      }, null, 8, Nb),
                      q(t("input", {
                        "onUpdate:modelValue": (l) => d.timeout = l,
                        class: "form-input",
                        style: { width: "100%" },
                        placeholder: p(a)("robotEditor.timeoutPlaceholder"),
                        spellcheck: "false"
                      }, null, 8, Rb), [
                        [le, d.timeout]
                      ])
                    ])
                  ])) : T("", !0),
                  R("kw", v, "return", d.returnValue) ? (i(), u("div", Fb, [
                    t("label", Pb, [
                      we(m(p(a)("robotEditor.returnValue")) + " ", 1),
                      d.returnValue ? T("", !0) : (i(), u("button", {
                        key: 0,
                        class: "meta-close-btn",
                        onClick: (l) => H("kw", v, "return")
                      }, "×", 8, Db))
                    ]),
                    t("span", Mb, [
                      t("span", {
                        class: "form-hl-overlay",
                        innerHTML: Te(d.returnValue)
                      }, null, 8, xb),
                      q(t("input", {
                        "onUpdate:modelValue": (l) => d.returnValue = l,
                        class: "form-input",
                        style: { width: "100%" },
                        placeholder: p(a)("robotEditor.returnValuePlaceholder"),
                        spellcheck: "false"
                      }, null, 8, Ub), [
                        [le, d.returnValue]
                      ])
                    ])
                  ])) : T("", !0)
                ])) : T("", !0),
                t("div", Bb, [
                  t("label", Kb, m(p(a)("robotEditor.steps")), 1),
                  t("div", Hb, [
                    (i(!0), u(x, null, X(d.steps, (l, S) => (i(), u("div", {
                      key: S,
                      class: "step-row",
                      style: Ee({ paddingLeft: st(d.steps, S) * 24 + "px" })
                    }, [
                      t("span", Ob, m(S + 1) + ".", 1),
                      q(t("select", {
                        "onUpdate:modelValue": (b) => l.type = b,
                        class: ne(["step-type-select", Ro(l.type)]),
                        onChange: (b) => vo(l)
                      }, [
                        (i(!0), u(x, null, X(p(Ta), (b) => (i(), u("option", {
                          key: b,
                          value: b
                        }, m(en(b)), 9, Wb))), 128))
                      ], 42, Ib), [
                        [it, l.type]
                      ]),
                      l.type === "keyword" ? (i(), u(x, { key: 0 }, [
                        t("div", jb, [
                          q(t("input", {
                            "onUpdate:modelValue": (b) => l.keyword = b,
                            class: "form-input step-keyword-input",
                            spellcheck: "false",
                            style: Ee({ width: Math.max(18, Math.min((l.keyword || "").length + 5, 60)) + "ch" }),
                            placeholder: p(a)("robotEditor.keywordPlaceholder"),
                            onFocus: (b) => qt(l),
                            onBlur: pt,
                            onKeydown: (b) => Tt(b, l),
                            onInput: (b) => zt(l)
                          }, null, 44, qb), [
                            [le, l.keyword]
                          ]),
                          Y.value === l && De.value.length > 0 ? (i(), u("div", {
                            key: 0,
                            ref_for: !0,
                            ref_key: "keywordDropdownRef",
                            ref: ce,
                            class: "keyword-dropdown"
                          }, [
                            (i(!0), u(x, null, X(De.value, (b, o) => {
                              var s;
                              return i(), u("div", {
                                key: b.name,
                                class: ne(["keyword-dropdown-item", { active: o === Z.value }]),
                                onMousedown: re(($) => Gt(l, b), ["prevent"])
                              }, [
                                t("span", Gb, m(b.name), 1),
                                (s = b.args) != null && s.length ? (i(), u("span", Yb, m(b.args.map(($) => $.replace(/^[$@&%]\{([^}]+)\}.*$/, "$1")).join(", ")), 1)) : T("", !0),
                                t("span", {
                                  class: ne(["kw-suggestion-source", "source-" + (b.source === "builtin" ? "builtin" : b.source === "local" || b.source === "project" ? "local" : "library")])
                                }, m(b.source === "builtin" ? p(a)("robotEditor.builtinKeyword") : b.source === "local" || b.source === "project" ? p(a)("robotEditor.localKeyword") : b.source), 3)
                              ], 42, zb);
                            }), 128))
                          ], 512)) : T("", !0)
                        ]),
                        l.args.length ? (i(), u("div", Xb, [
                          (i(!0), u(x, null, X(l.args, (b, o) => (i(), u("span", {
                            key: o,
                            class: "step-arg-chip arg-ac-wrapper"
                          }, [
                            $e(l)[o] ? (i(), u("span", Zb, m($e(l)[o]), 1)) : T("", !0),
                            t("span", {
                              class: "arg-highlight-wrap",
                              style: Ee({ width: lt(l.args[o]) })
                            }, [
                              t("span", {
                                class: "arg-highlight-overlay",
                                innerHTML: Te(l.args[o])
                              }, null, 8, Qb),
                              q(t("input", {
                                "onUpdate:modelValue": (s) => l.args[o] = s,
                                class: "step-arg-input",
                                spellcheck: "false",
                                onScroll: Xt,
                                style: { width: "100%" },
                                placeholder: $e(l)[o] || p(a)("robotEditor.argPlaceholder"),
                                onInput: (s) => Dt(s.target.value, `${S}-${o}`),
                                onKeydown: (s) => Mt(s, l, o),
                                onBlur: ho,
                                autocomplete: "off"
                              }, null, 40, Jb), [
                                [le, l.args[o]]
                              ])
                            ], 4),
                            be.value === `${S}-${o}` && oe.value.length ? (i(), u("div", ew, [
                              (i(!0), u(x, null, X(oe.value, (s, $) => (i(), u("div", {
                                key: s,
                                class: ne(["arg-ac-item", { active: $ === fe.value }]),
                                onMousedown: re((K) => Yt(l, o, s), ["prevent"])
                              }, m(s), 43, tw))), 128))
                            ])) : T("", !0),
                            t("button", {
                              class: "chip-remove",
                              onClick: (s) => Qe(l, o)
                            }, "×", 8, ow)
                          ]))), 128))
                        ])) : T("", !0),
                        t("button", {
                          class: ne(["step-btn step-add-arg", { "has-hint": $t(l) !== "+" }]),
                          onClick: (b) => dt(l),
                          title: p(a)("robotEditor.addArg")
                        }, m($t(l)), 11, nw)
                      ], 64)) : l.type === "assignment" ? (i(), u(x, { key: 1 }, [
                        (i(!0), u(x, null, X(l.returnVars, (b, o) => (i(), u("span", {
                          key: o,
                          class: "step-var-chip"
                        }, [
                          q(t("input", {
                            "onUpdate:modelValue": (s) => l.returnVars[o] = s,
                            class: "step-var-input",
                            spellcheck: "false",
                            placeholder: "${var}",
                            onBlur: (s) => yo(l, o)
                          }, null, 40, aw), [
                            [le, l.returnVars[o]]
                          ]),
                          t("button", {
                            class: "chip-remove",
                            onClick: (s) => Jo(l, o)
                          }, "×", 8, sw)
                        ]))), 128)),
                        t("button", {
                          class: "step-btn step-add-var",
                          onClick: (b) => at(l),
                          title: "+var"
                        }, "+v", 8, lw),
                        h[18] || (h[18] = t("span", { class: "step-assign-eq" }, "=", -1)),
                        t("div", rw, [
                          q(t("input", {
                            "onUpdate:modelValue": (b) => l.keyword = b,
                            class: "form-input step-keyword-input",
                            spellcheck: "false",
                            style: Ee({ width: Math.max(18, Math.min((l.keyword || "").length + 5, 60)) + "ch" }),
                            placeholder: p(a)("robotEditor.keywordPlaceholder"),
                            onFocus: (b) => qt(l),
                            onBlur: pt,
                            onKeydown: (b) => Tt(b, l),
                            onInput: (b) => zt(l)
                          }, null, 44, iw), [
                            [le, l.keyword]
                          ]),
                          Y.value === l && De.value.length > 0 ? (i(), u("div", {
                            key: 0,
                            ref_for: !0,
                            ref_key: "keywordDropdownRef",
                            ref: ce,
                            class: "keyword-dropdown"
                          }, [
                            (i(!0), u(x, null, X(De.value, (b, o) => {
                              var s;
                              return i(), u("div", {
                                key: b.name,
                                class: ne(["keyword-dropdown-item", { active: o === Z.value }]),
                                onMousedown: re(($) => Gt(l, b), ["prevent"])
                              }, [
                                t("span", cw, m(b.name), 1),
                                (s = b.args) != null && s.length ? (i(), u("span", dw, m(b.args.map(($) => $.replace(/^[$@&%]\{([^}]+)\}.*$/, "$1")).join(", ")), 1)) : T("", !0),
                                t("span", {
                                  class: ne(["kw-suggestion-source", "source-" + (b.source === "builtin" ? "builtin" : b.source === "local" || b.source === "project" ? "local" : "library")])
                                }, m(b.source === "builtin" ? p(a)("robotEditor.builtinKeyword") : b.source === "local" || b.source === "project" ? p(a)("robotEditor.localKeyword") : b.source), 3)
                              ], 42, uw);
                            }), 128))
                          ], 512)) : T("", !0)
                        ]),
                        l.args.length ? (i(), u("div", pw, [
                          (i(!0), u(x, null, X(l.args, (b, o) => (i(), u("span", {
                            key: o,
                            class: "step-arg-chip arg-ac-wrapper"
                          }, [
                            $e(l)[o] ? (i(), u("span", fw, m($e(l)[o]), 1)) : T("", !0),
                            t("span", {
                              class: "arg-highlight-wrap",
                              style: Ee({ width: lt(l.args[o]) })
                            }, [
                              t("span", {
                                class: "arg-highlight-overlay",
                                innerHTML: Te(l.args[o])
                              }, null, 8, mw),
                              q(t("input", {
                                "onUpdate:modelValue": (s) => l.args[o] = s,
                                class: "step-arg-input",
                                spellcheck: "false",
                                onScroll: Xt,
                                style: { width: "100%" },
                                placeholder: $e(l)[o] || p(a)("robotEditor.argPlaceholder"),
                                onInput: (s) => Dt(s.target.value, `${S}-${o}`),
                                onKeydown: (s) => Mt(s, l, o),
                                onBlur: ho,
                                autocomplete: "off"
                              }, null, 40, gw), [
                                [le, l.args[o]]
                              ])
                            ], 4),
                            be.value === `${S}-${o}` && oe.value.length ? (i(), u("div", vw, [
                              (i(!0), u(x, null, X(oe.value, (s, $) => (i(), u("div", {
                                key: s,
                                class: ne(["arg-ac-item", { active: $ === fe.value }]),
                                onMousedown: re((K) => Yt(l, o, s), ["prevent"])
                              }, m(s), 43, hw))), 128))
                            ])) : T("", !0),
                            t("button", {
                              class: "chip-remove",
                              onClick: (s) => Qe(l, o)
                            }, "×", 8, yw)
                          ]))), 128))
                        ])) : T("", !0),
                        t("button", {
                          class: ne(["step-btn step-add-arg", { "has-hint": $t(l) !== "+" }]),
                          onClick: (b) => dt(l),
                          title: p(a)("robotEditor.addArg")
                        }, m($t(l)), 11, bw)
                      ], 64)) : l.type === "var" ? (i(), u(x, { key: 2 }, [
                        t("span", ww, [
                          q(t("input", {
                            "onUpdate:modelValue": (b) => l.returnVars[0] = b,
                            class: "step-var-input",
                            spellcheck: "false",
                            placeholder: "${var}",
                            onBlur: (b) => yo(l, 0)
                          }, null, 40, _w), [
                            [le, l.returnVars[0]]
                          ])
                        ]),
                        h[20] || (h[20] = t("span", { class: "step-assign-eq" }, "=", -1)),
                        (i(!0), u(x, null, X(l.args, (b, o) => (i(), u("span", {
                          key: o,
                          class: "step-arg-chip"
                        }, [
                          t("span", {
                            class: "arg-highlight-wrap",
                            style: Ee({ width: lt(l.args[o]) })
                          }, [
                            t("span", {
                              class: "arg-highlight-overlay",
                              innerHTML: Te(l.args[o])
                            }, null, 8, kw),
                            q(t("input", {
                              "onUpdate:modelValue": (s) => l.args[o] = s,
                              class: "step-arg-input",
                              spellcheck: "false",
                              onScroll: Xt,
                              style: { width: "100%" },
                              placeholder: p(a)("robotEditor.valuePlaceholder")
                            }, null, 40, Cw), [
                              [le, l.args[o]]
                            ])
                          ], 4),
                          t("button", {
                            class: "chip-remove",
                            onClick: (s) => Qe(l, o)
                          }, "×", 8, $w)
                        ]))), 128)),
                        t("button", {
                          class: "step-btn step-add-arg",
                          onClick: (b) => dt(l),
                          title: p(a)("robotEditor.addValue")
                        }, "+", 8, Tw),
                        q(t("select", {
                          "onUpdate:modelValue": (b) => l.varScope = b,
                          class: "step-scope-select"
                        }, [
                          h[19] || (h[19] = t("option", { value: "" }, "scope", -1)),
                          (i(!0), u(x, null, X(p(Ea), (b) => (i(), u("option", {
                            key: b,
                            value: b
                          }, m(b), 9, Sw))), 128))
                        ], 8, Ew), [
                          [it, l.varScope]
                        ])
                      ], 64)) : l.type === "for" ? (i(), u(x, { key: 3 }, [
                        t("span", Lw, [
                          t("span", {
                            class: "form-hl-overlay hl-mono",
                            innerHTML: Te(l.loopVar)
                          }, null, 8, Vw),
                          q(t("input", {
                            "onUpdate:modelValue": (b) => l.loopVar = b,
                            class: "form-input step-var-input-inline",
                            spellcheck: "false",
                            style: { width: "100%" },
                            placeholder: "${item}"
                          }, null, 8, Aw), [
                            [le, l.loopVar]
                          ])
                        ]),
                        q(t("select", {
                          "onUpdate:modelValue": (b) => l.loopFlavor = b,
                          class: "step-flavor-select"
                        }, [
                          (i(!0), u(x, null, X(p(ea), (b) => (i(), u("option", {
                            key: b,
                            value: b
                          }, m(b), 9, Rw))), 128))
                        ], 8, Nw), [
                          [it, l.loopFlavor]
                        ]),
                        (i(!0), u(x, null, X(l.loopValues, (b, o) => (i(), u("span", {
                          key: o,
                          class: "step-arg-chip"
                        }, [
                          q(t("input", {
                            "onUpdate:modelValue": (s) => l.loopValues[o] = s,
                            class: "step-arg-input",
                            placeholder: p(a)("robotEditor.valuePlaceholder")
                          }, null, 8, Fw), [
                            [le, l.loopValues[o]]
                          ]),
                          t("button", {
                            class: "chip-remove",
                            onClick: (s) => No(l, o)
                          }, "×", 8, Pw)
                        ]))), 128)),
                        t("button", {
                          class: "step-btn step-add-arg",
                          onClick: (b) => ao(l),
                          title: p(a)("robotEditor.addValue")
                        }, "+", 8, Dw)
                      ], 64)) : l.type === "if" || l.type === "else_if" || l.type === "while" ? (i(), u("span", Mw, [
                        t("span", {
                          class: "form-hl-overlay hl-condition",
                          innerHTML: Te(l.condition)
                        }, null, 8, xw),
                        q(t("input", {
                          "onUpdate:modelValue": (b) => l.condition = b,
                          class: "form-input step-condition-input",
                          style: { width: "100%" },
                          placeholder: p(a)("robotEditor.conditionPlaceholder"),
                          spellcheck: "false"
                        }, null, 8, Uw), [
                          [le, l.condition]
                        ])
                      ])) : l.type === "except" ? (i(), u(x, { key: 5 }, [
                        q(t("input", {
                          "onUpdate:modelValue": (b) => l.exceptPattern = b,
                          class: "form-input step-keyword-input",
                          spellcheck: "false",
                          placeholder: p(a)("robotEditor.exceptPatternPlaceholder")
                        }, null, 8, Bw), [
                          [le, l.exceptPattern]
                        ]),
                        l.exceptVar || l.exceptPattern ? (i(), u("span", Kw, "AS")) : T("", !0),
                        l.exceptVar || l.exceptPattern ? (i(), u("span", Hw, [
                          t("span", {
                            class: "form-hl-overlay hl-mono",
                            innerHTML: Te(l.exceptVar)
                          }, null, 8, Ow),
                          q(t("input", {
                            "onUpdate:modelValue": (b) => l.exceptVar = b,
                            class: "form-input step-var-input-inline",
                            spellcheck: "false",
                            style: { width: "100%" },
                            placeholder: "${error}"
                          }, null, 8, Iw), [
                            [le, l.exceptVar]
                          ])
                        ])) : T("", !0)
                      ], 64)) : l.type === "return" ? (i(), u(x, { key: 6 }, [
                        (i(!0), u(x, null, X(l.args, (b, o) => (i(), u("span", {
                          key: o,
                          class: "step-arg-chip"
                        }, [
                          q(t("input", {
                            "onUpdate:modelValue": (s) => l.args[o] = s,
                            class: "step-arg-input",
                            placeholder: p(a)("robotEditor.valuePlaceholder")
                          }, null, 8, Ww), [
                            [le, l.args[o]]
                          ]),
                          t("button", {
                            class: "chip-remove",
                            onClick: (s) => Qe(l, o)
                          }, "×", 8, jw)
                        ]))), 128)),
                        t("button", {
                          class: "step-btn step-add-arg",
                          onClick: (b) => dt(l),
                          title: p(a)("robotEditor.addValue")
                        }, "+", 8, qw)
                      ], 64)) : l.type === "comment" ? q((i(), u("input", {
                        key: 7,
                        "onUpdate:modelValue": (b) => l.comment = b,
                        class: "form-input flex-1 step-comment-input",
                        spellcheck: "false",
                        placeholder: "# ..."
                      }, null, 8, zw)), [
                        [le, l.comment]
                      ]) : T("", !0),
                      t("div", Gw, [
                        t("button", {
                          class: "step-btn",
                          onClick: (b) => go(d.steps, S, -1),
                          disabled: S === 0
                        }, "↑", 8, Yw),
                        t("button", {
                          class: "step-btn",
                          onClick: (b) => go(d.steps, S, 1),
                          disabled: S === d.steps.length - 1
                        }, "↓", 8, Xw),
                        t("button", {
                          class: "step-btn danger",
                          onClick: (b) => mo(d.steps, S),
                          title: p(a)("editor.common.delete")
                        }, "×", 8, Zw)
                      ])
                    ], 4))), 128)),
                    t("div", Qw, [
                      t("button", {
                        class: "add-step-btn",
                        onClick: (l) => no(d.steps)
                      }, "+ " + m(p(a)("robotEditor.addStep")), 9, Jw),
                      t("button", {
                        class: "add-step-btn add-block",
                        onClick: (l) => no(d.steps, "var")
                      }, "+ VAR", 8, e_),
                      t("button", {
                        class: "add-step-btn add-block",
                        onClick: (l) => Ct(d.steps, "for")
                      }, "+ FOR", 8, t_),
                      t("button", {
                        class: "add-step-btn add-block",
                        onClick: (l) => Ct(d.steps, "if")
                      }, "+ IF", 8, o_),
                      t("button", {
                        class: "add-step-btn add-block",
                        onClick: (l) => Ct(d.steps, "while")
                      }, "+ WHILE", 8, n_),
                      t("button", {
                        class: "add-step-btn add-block",
                        onClick: (l) => Ct(d.steps, "try")
                      }, "+ TRY", 8, a_)
                    ])
                  ])
                ])
              ], 512), [
                [eo, !J.value.has(v)]
              ])
            ]))), 128))
          ], 512), [
            [eo, !ue.value]
          ])
        ])
      ], 512), [
        [eo, y.value === "visual"]
      ]),
      y.value === "flow" ? (i(), u("div", s_, [
        ve(If, {
          form: Q,
          sidecar: P.value,
          "onUpdate:sidecar": Ht,
          onAddTestCase: bo,
          onAddKeyword: Zt,
          onLibrariesChanged: kt
        }, null, 8, ["form", "sidecar"])
      ])) : T("", !0),
      q(t("div", {
        class: "code-editor",
        ref_key: "codeEditorContainer",
        ref: A
      }, null, 512), [
        [eo, y.value === "code"]
      ]),
      ve(Io, {
        modelValue: ct.value,
        "onUpdate:modelValue": h[8] || (h[8] = (d) => ct.value = d),
        title: p(a)("robotEditor.libraryInstallDialogTitle", { library: Rt.value }),
        size: "sm"
      }, {
        footer: _e(() => [
          ve(Oe, {
            variant: "secondary",
            disabled: Ot.value,
            onClick: An
          }, {
            default: _e(() => [
              we(m(p(a)("editor.common.cancel")), 1)
            ]),
            _: 1
          }, 8, ["disabled"]),
          ve(Oe, {
            variant: "primary",
            disabled: Ot.value || !Ft.value.trim(),
            "data-testid": "lib-install-confirm",
            onClick: Vn
          }, {
            default: _e(() => [
              we(m(Ot.value ? p(a)("robotEditor.libraryInstallDialogInstalling") : p(a)("robotEditor.libraryInstallDialogConfirm")), 1)
            ]),
            _: 1
          }, 8, ["disabled"])
        ]),
        default: _e(() => [
          t("p", l_, m(p(a)("robotEditor.libraryInstallDialogIntro", {
            library: Rt.value,
            env: Xo.value
          })), 1),
          t("label", r_, [
            we(m(p(a)("robotEditor.libraryInstallDialogPipName")) + " ", 1),
            q(t("input", {
              "onUpdate:modelValue": h[7] || (h[7] = (d) => Ft.value = d),
              type: "text",
              class: "lib-install-pkg-input",
              autocomplete: "off",
              spellcheck: "false",
              "data-testid": "lib-install-pkg-input",
              disabled: Ot.value
            }, null, 8, i_), [
              [le, Ft.value]
            ]),
            t("small", u_, m(p(a)("robotEditor.libraryInstallDialogPipHint")), 1)
          ]),
          It.value ? (i(), u("div", c_, m(It.value), 1)) : T("", !0)
        ]),
        _: 1
      }, 8, ["modelValue", "title"])
    ], 6));
  }
}), N_ = /* @__PURE__ */ qe(d_, [["__scopeId", "data-v-8d9875d7"]]), p_ = {
  robotEditor: {
    visualTab: "可视化编辑器",
    flowTab: "流程",
    codeTab: "代码",
    heal: {
      toggleOn: "自愈：开",
      toggleOff: "自愈：关",
      toggleOnHint: "单击可将此文件中的每个 Heal* 关键字还原为其原始的 Browser 库形式，并移除 RoboScopeHeal 导入。",
      toggleOffHint: "单击可将每个 Browser 关键字（Click、Fill Text 等）提升为其 Heal 变体，并添加 RoboScopeHeal 库导入。",
      toastEnabled: "已将 {count} 个关键字切换为自愈。",
      toastDisabled: "已将 {count} 个 Heal 关键字还原为其原始形式。"
    },
    libraryNotInEnvTitle: '库 "{library}" 未安装在环境中',
    libraryNotInEnvBody: "库导入已添加到文件，但 {library} 未安装在此仓库的环境中。在你安装该软件包之前，仅显示示例关键字——请打开“环境”视图进行添加。",
    libraryNoEnvAtAllBody: "库导入已添加到 {library}，但此仓库未配置环境。请在“环境”视图中配置一个以安装库。",
    libraryInstallDialogTitle: '安装 "{library}"？',
    libraryInstallDialogIntro: '库 "{library}" 已导入，但未安装在环境 "{env}" 中。是否立即通过 pip 安装它，以便其完整的关键字列表显示在调色板中？',
    libraryInstallDialogPipName: "Pip 软件包名称",
    libraryInstallDialogPipHint: "如果自动检测的名称有误，你可以编辑此处。",
    libraryInstallDialogConfirm: "安装",
    libraryInstallDialogInstalling: "正在安装…",
    libraryInstallStartedTitle: "已开始安装",
    libraryInstallStartedBody: "{package} 正在安装。安装完成后调色板将刷新。",
    libraryInstallFailed: "安装失败。",
    libraryAlreadyProvidedTitle: '库 "{library}" 已可用',
    libraryAlreadyProvidedBody: '{library} 由 "{provider}" 提供，且其已安装在环境中。关键字缓存可能已过时——请尝试重启 rf-mcp 服务器（设置 → AI → RF MCP），或稍候片刻并切回此文件以刷新。',
    tests: "测试",
    keywordsCount: "关键字",
    initFileWarning: "套件初始化文件（__init__.robot）不能包含测试用例部分。",
    parseError: "解析错误",
    expandAll: "全部展开",
    collapseAll: "全部折叠",
    settingsSection: "设置",
    variablesSection: "变量",
    testCasesSection: "测试用例",
    keywordsSection: "关键字",
    addSetting: "添加设置",
    addVariable: "添加变量",
    addTestCase: "添加测试用例",
    addKeyword: "添加关键字",
    addStep: "添加步骤",
    addTag: "添加标签...",
    noSettings: "尚未定义设置。单击“添加设置”以添加一个。",
    noVariables: "尚未定义变量。单击“添加变量”以添加一个。",
    noTestCases: "尚无测试用例。单击“添加测试用例”以创建一个。",
    noKeywords: "尚未定义关键字。单击“添加关键字”以创建一个。",
    unnamedTestCase: "未命名的测试用例",
    unnamedKeyword: "未命名的关键字",
    steps: "步骤",
    testCaseName: "测试用例名称",
    testCaseNamePlaceholder: "我的测试用例",
    keywordName: "关键字名称",
    keywordNamePlaceholder: "我的关键字",
    documentation: "文档",
    documentationPlaceholder: "可选文档...",
    tags: "标签",
    setup: "Setup",
    setupPlaceholder: "Setup 关键字（可选）",
    teardown: "Teardown",
    teardownPlaceholder: "Teardown 关键字（可选）",
    timeout: "超时",
    timeoutPlaceholder: "例如 30s",
    template: "模板",
    templatePlaceholder: "模板关键字（可选）",
    arguments: "参数",
    argumentsPlaceholder: "${arg1}    ${arg2}",
    addArgument: "添加参数，例如 ${name}",
    argTypes: {
      required: "必需",
      optional: "可选",
      varargs: "可变参数",
      kwargs: "键值",
      namedOnly: "仅命名",
      namedOnlyBelow: "以下为仅命名参数",
      namePlaceholder: "name",
      defaultPlaceholder: "默认值",
      addDefault: "添加默认值"
    },
    returnValue: "返回值",
    returnValuePlaceholder: "${result}",
    stepPlaceholder: "Log    Hello World",
    settingValuePlaceholder: "值",
    argPlaceholder: "arg",
    variableNamePlaceholder: "${VARIABLE_NAME}",
    variableValuePlaceholder: "值",
    keywordPlaceholder: "关键字名称",
    conditionPlaceholder: "${x} > 5",
    valuePlaceholder: "值",
    exceptPatternPlaceholder: "错误模式",
    addArg: "添加参数",
    addValue: "添加值",
    settingType: {
      Documentation: "Documentation",
      Library: "Library",
      Resource: "Resource",
      Variables: "Variables",
      SuiteSetup: "Suite Setup",
      SuiteTeardown: "Suite Teardown",
      TestSetup: "Test Setup",
      TestTeardown: "Test Teardown",
      TestTemplate: "Test Template",
      TestTimeout: "Test Timeout",
      ForceTags: "Force Tags",
      DefaultTags: "Default Tags",
      Metadata: "Metadata"
    },
    stepType: {
      keyword: "关键字",
      assignment: "赋值",
      var: "VAR",
      comment: "注释",
      for: "FOR",
      if: "IF",
      else_if: "ELSE IF",
      else: "ELSE",
      while: "WHILE",
      try: "TRY",
      except: "EXCEPT",
      finally: "FINALLY",
      end: "END",
      break: "BREAK",
      continue: "CONTINUE",
      return: "RETURN"
    },
    builtinKeyword: "内置",
    localKeyword: "本地",
    noSuggestions: "没有匹配的关键字"
  },
  flowEditor: {
    noTestCasesYet: "此文件中尚无测试用例。",
    noKeywordsYet: "此文件中尚无关键字。",
    addTestCase: "测试用例",
    addTestCaseTitle: "添加一个新的空测试用例",
    addKeyword: "关键字",
    addKeywordTitle: "添加一个新的空关键字（用户自定义关键字）",
    libraries: "库",
    librariesTitle: "管理此文件的库和资源导入",
    librariesNone: "尚无库或资源导入。",
    libraryAdd: "添加",
    libraryInputPlaceholder: "库名称（例如 Browser）或 resource/path.resource",
    libraryRemoveTitle: "移除 {name}",
    variables: "变量",
    variablesTitle: "为此文件定义套件变量（*** Variables ***）",
    variablesNone: "尚未定义变量。",
    variableNamePlaceholder: "${NAME}",
    variableValuePlaceholder: "值",
    variableAdd: "添加",
    variableRemoveTitle: "移除 {name}",
    envVarsUsed: "已使用的环境变量：",
    envVarDefined: "已在环境 {env} 中定义",
    envVarDefaultUsed: "环境 {env} 中未定义，已使用行内默认值",
    envVarMissing: "环境 {env} 中未定义；除非执行端自己的环境提供该变量，否则运行会失败。",
    envVarCheckedAgainst: "对照环境 {env} 校验",
    envVarNotDefined: "（未定义）",
    templateAddRow: "添加行",
    templateAddColumn: "添加列",
    templateRemoveRow: "移除行",
    suiteSettings: "设置",
    suiteSettingsTitle: "套件设置（Setup/Teardown、Tags、Documentation、Metadata）",
    suiteSettingsNone: "尚无套件设置。",
    suiteSettingRemoveTitle: "移除 {name}",
    keywordNotImportedHint: '库 "{library}" 未在此文件中导入。选择此关键字将自动添加该导入。',
    keywordNotImportedBadgeTitle: "添加此关键字时将自动导入该库。",
    examplesCategoryBadge: "（示例）",
    examplesCategoryHint: "由于动态库自省未返回任何数据，因此显示精选子集——通常是因为该仓库未配置环境，或该库未安装在环境中。配置环境以查看完整的关键字列表。",
    currentFileCategoryBadge: "此文件",
    currentFileCategoryHint: "在你当前正在编辑的文件中定义的关键字。",
    resourcesSectionLabel: "你的资源",
    resourcesSectionHint: "来自本仓库 .robot / .resource 文件的关键词。插入时会自动补上对应的 Resource 导入。",
    resourcePanelLabel: "资源",
    librariesPanelLabel: "库",
    importAdded: {
      resourceTitle: "已导入资源",
      libraryTitle: "已导入库",
      message: "已将 {name} 加入 *** Settings ***，运行时即可解析该关键词。"
    },
    sort: {
      title: "关键词排序",
      mostUsed: "最常用优先",
      alpha: "A–Z",
      importedFirst: "已导入优先"
    },
    filter: {
      title: "筛选显示范围",
      resources: "你的资源",
      importedLibs: "已导入的库",
      exampleLibs: "示例库（未安装）",
      builtin: "内置库（BuiltIn）",
      hidingCategories: "已隐藏 {count} 类",
      clear: "全部显示"
    },
    resizePanelHint: "拖动以调整详情面板大小",
    startNodeLabel: "开始",
    endNodeLabel: "结束",
    keyword: "关键字",
    arguments: "参数",
    condition: "条件",
    returnVars: "返回变量",
    returnValues: "返回值",
    returnValuePlaceholder: '${result}、${count}、"literal"…',
    returnNoValue: "（无值）",
    docMeta: {
      label: "文档",
      placeholder: "描述此测试用例（或关键字）的作用——将显示在 HTML 报告中。",
      hint: "多行文本在保存的 .robot 文件中以 `...` 续行的形式保留。",
      addTitle: "为此测试用例/关键字添加 [Documentation] 旁注",
      removeTitle: "移除文档",
      alreadyPresent: "已附加 [Documentation]——单击旁注进行编辑。"
    },
    debug: {
      btnLabel: "运行到此处",
      btnTitle: "运行此测试直至所选步骤，并在调试模式下暂停",
      unresolvedLine: "无法解析此步骤的源代码行——请保存文件后重试。",
      startFailed: "无法启动调试会话。",
      unsavedHintTitle: "调试前请先保存",
      unsavedHint: "你有未保存的更改。请先保存文件，以使调试断点与磁盘上的源代码匹配。",
      confirmReplaceTitle: "替换活动的调试会话？",
      confirmReplace: "此文件的调试会话已在第 {lineFrom} 行暂停。是否断开它并在第 {lineTo} 行启动新会话？",
      confirmReplaceConfirm: "断开并启动"
    },
    heal: {
      toggleLabel: "自愈",
      toggleHint: "将此 Browser 关键字替换为其 Heal 变体（页面布局漂移时定位符会自动恢复）。"
    },
    settingMeta: {
      allPresent: "每个 [...] 设置均已附加——单击旁注进行编辑。",
      emptyHint: "单击以编辑",
      documentation: {
        label: "文档",
        placeholder: "描述此测试用例（或关键字）的作用——将显示在 HTML 报告中。",
        hint: "多行文本在保存的 .robot 文件中以 `...` 续行的形式保留。",
        addTitle: "添加 [Documentation] 旁注",
        removeTitle: "移除文档"
      },
      tags: {
        label: "标签",
        placeholder: "smoke, regression, slow",
        hint: "以逗号分隔。标签可在运行时通过 --include / --exclude 进行选择/排除。",
        addTitle: "为此测试用例/关键字添加 [Tags]",
        removeTitle: "移除标签"
      },
      arguments: {
        label: "参数",
        placeholder: "${name}, ${greeting}=Hello",
        hint: "以逗号分隔。可选参数使用 ${name}=default，可变参数使用 @{name}。",
        addTitle: "为此关键字添加 [Arguments]",
        removeTitle: "移除参数"
      },
      setup: {
        label: "Setup",
        placeholder: "Setup 关键字名称",
        hint: "在此测试用例/关键字主体之前执行的 Robot 关键字。会覆盖 Test Setup。",
        addTitle: "添加 [Setup] 钩子",
        removeTitle: "移除 setup"
      },
      teardown: {
        label: "Teardown",
        placeholder: "Teardown 关键字名称",
        hint: "在主体之后执行的 Robot 关键字，即使失败也会执行。会覆盖 Test Teardown。",
        addTitle: "添加 [Teardown] 钩子",
        removeTitle: "移除 teardown"
      },
      template: {
        label: "模板",
        placeholder: "模板关键字名称",
        hint: "测试主体中的每一行都会成为以该行为参数对此关键字的一次调用。",
        addTitle: "添加 [Template]（数据驱动测试）",
        removeTitle: "移除模板"
      },
      timeout: {
        label: "超时",
        placeholder: "30s、5 minutes",
        hint: "此测试用例/关键字被强制中止前的最大运行时间。",
        addTitle: "添加 [Timeout]",
        removeTitle: "移除超时"
      }
    },
    testCaseSettings: "测试用例设置",
    keywordSettings: "关键字设置",
    comment: "注释",
    palette: "关键字",
    searchKeywords: "搜索关键字...",
    noResults: "没有匹配的关键字。",
    addArg: "添加参数",
    addVar: "添加变量",
    addValue: "添加值",
    loopVar: "循环变量",
    loopFlavor: "循环类型",
    loopValues: "值",
    exceptPattern: "错误模式",
    exceptVar: "错误变量",
    varName: "变量名称",
    varScope: "作用域",
    selector: {
      tooltipHasCandidates: "{count} 个已录制的候选项——单击以切换",
      customValueHint: "（自定义值，非来自录制）",
      candidatesBadge: "× {count}",
      replaceCustomConfirm: `是否将你的自定义定位符

  {current}

替换为已录制的候选项

  {next}
？`
    },
    argLabels: {
      fallback: "参数 {n}",
      extraPositional: "额外的位置参数",
      extraNamed: "额外的命名参数",
      defaultPlaceholder: "默认值：{default}"
    },
    addArgPicker: {
      custom: "自定义值 …",
      customLabel: "自定义值",
      customPlaceholder: "value 或 name=value",
      customAdd: "添加",
      noUnused: "所有参数均已添加。",
      namedHint: "命名"
    },
    docModal: {
      title: "关键字文档",
      argsHeader: "参数",
      noDoc: "无可用文档。",
      button: "显示文档",
      loading: "正在加载文档…"
    },
    argTypes: {
      text: "文本",
      integer: "整数",
      number: "数字",
      yesNo: "开 / 关",
      duration: "持续时间",
      durationHint: "例如 5s、1min、00:00:05",
      path: "文件路径",
      any: "任意",
      choice: "选项",
      collection: "列表 / 映射",
      unknown: "值",
      optionalSuffix: "（可选）",
      customValue: "自定义",
      toggleToText: "切换到文本输入（例如用于变量）",
      toggleBackToTyped: "切换回类型化输入"
    }
  },
  debug: {
    btn: {
      label: "调试",
      tooltip: "重新运行此测试并在失败行暂停"
    },
    panel: {
      paused: "已暂停",
      terminated: "已终止",
      notPaused: "等待首次停止…",
      lineLabel: "行",
      callStack: "调用栈",
      callStackEmpty: "尚无调用栈 — 执行尚未暂停。",
      scopes: "变量",
      scopesEmpty: "无可用作用域。",
      scopeEmpty: "此作用域中无变量。",
      output: "输出",
      outputEmpty: "尚无输出。",
      toolbar: {
        continue: "继续",
        stepOver: "单步跳过",
        stepIn: "单步进入",
        stepOut: "单步跳出",
        stop: "停止"
      }
    },
    error: {
      title: "调试会话出错",
      startFailed: "无法启动调试会话。",
      commandFailed: "调试命令失败。"
    },
    prereq: {
      title: "调试器未安装",
      body: "{package} 未安装在本项目的 Python 环境中。本平台需要它来运行交互式调试。现在安装吗？",
      install: "安装",
      cancel: "取消",
      installing: "正在安装 {package}…",
      installFailed: "安装失败。详情请查看日志。",
      installSuccess: "{package} 安装成功。"
    }
  },
  recorder: {
    title: "录制",
    record: "录制",
    startRecording: "录制浏览器交互",
    stop: "停止录制",
    events: "个事件",
    generating: "正在生成 .robot 文件…",
    newRecording: "新建录制",
    tryAgain: "重试",
    cancelled: "录制已取消",
    waitingForEvents: "正在等待浏览器交互…",
    generatedFile: "已生成的 .robot 文件",
    completed: "录制完成",
    completedMsg: "Robot Framework 测试文件已生成。",
    failed: "录制失败",
    failedMsg: "录制过程中发生错误。",
    selector: {
      swapAriaLabel: "切换选择器策略",
      editOrAddAriaLabel: "编辑选择器或添加自定义选择器",
      editTitle: "编辑此选择器",
      addCustom: "添加自定义选择器",
      valuePlaceholder: "css=.btn-primary, //button[…], text=Click me, …",
      strategyLabel: "策略",
      verifiedUniqueTitle: "在录制时已在实时页面上验证唯一",
      effectiveLabel: "生效值",
      effectivePlaceholder: "iframe[…] >>> selector >> nth=0",
      effectiveTitle: "逐字写入 .robot 文件的内容。默认值：由策略 + 值 + iframe 链自动组合而成。此处的自定义值将覆盖该组合。",
      effectiveResetTitle: "重置为自动组合的值",
      effectiveOverrideTitle: "覆盖已生效——替换 iframe 包裹、xpath= / text= 前缀和 nth=0",
      effectiveOverrideBadge: "覆盖已生效",
      strategy: {
        testid: "Test ID",
        aria: "ARIA",
        text: "文本",
        css: "CSS",
        xpath: "XPath",
        pw_locator: "Playwright",
        automation_id: "AutomationId",
        uia_name: "UIA Name",
        uia_class_name: "UIA Class"
      }
    },
    launcher: {
      heading: "开始录制",
      hint: "录制一个用户流程并将其转换为 Robot Framework 测试。浏览器将在受控窗口中打开——无需安装扩展。",
      repoLabel: "保存到仓库",
      targetUrlLabel: "打开 URL（可选）",
      targetUrlPlaceholder: "https://example.com",
      targetUrlHint: "留空则从 about:blank 开始——浏览器打开后您可以手动导航。",
      targetUrlInvalid: "URL 必须以 http:// 或 https:// 开头",
      transportLabel: "您想录制什么？",
      transport: {
        web: "Web——基于浏览器的流程",
        windows: "桌面（Windows）",
        macos: "桌面（macOS）"
      },
      comingSoon: "即将推出",
      record: "开始录制",
      starting: "正在启动…",
      startFailed: "无法开始录制。请重试。",
      remote: {
        heading: "此服务器上无法进行浏览器录制",
        body: "本平台后端没有可视化浏览器（远程服务器部署的典型情况），因此无法为您打开 Chromium 窗口进行录制。请改为在您自己的机器上安装浏览器录制扩展——它会在您的浏览器中录制并将事件流式传输到此后端。",
        override: "后端管理员可通过服务端环境变量强制启用浏览器录制。"
      },
      reset: {
        hint: "录制卡住或浏览器窗口无法打开？点击重置以清理任何残留的会话和孤立的浏览器进程。",
        label: "重置卡住的录制",
        busy: "正在重置…",
        noneFound: "未找到卡住的录制。",
        done: "重置完成",
        doneDetail: "已清理 {count} 个卡住的录制会话。",
        failed: "重置失败。请重试。"
      }
    },
    live: {
      heading: "录制",
      status: {
        connecting: "连接中",
        live: "实时",
        done: "完成",
        error: "错误"
      },
      lifecycle: {
        connecting: "连接中…",
        browser_starting: "正在启动 Chromium…",
        browser_ready: "浏览器就绪",
        browser_restarting: "正在重启浏览器…",
        browser_crashed: "浏览器崩溃",
        done: "完成",
        error: "错误"
      },
      uptimeTitle: "自浏览器就绪以来的时间",
      effectiveTitle: "在 .robot 文件中作为关键字第一个参数的确切文本。",
      restartBrowser: "重启浏览器",
      restartingBrowser: "正在重启…",
      restartBrowserHint: "终止当前的 Chromium 并启动一个新的。已捕获的命令会被保留；您可能需要重新导航到您的测试页面。",
      restartFailed: "无法重启浏览器。",
      crashTitle: "浏览器崩溃：",
      waiting: "正在等待您的第一次交互…",
      pathLabel: "另存为",
      pathPlaceholder: "flows/recording",
      save: "保存",
      stopAndSave: "停止并保存",
      deleteStep: "删除此步骤",
      streamError: "与录制器的连接已断开。",
      startFailed: "无法启动浏览器。",
      saveFailed: "无法保存录制。",
      repoMissing: "仓库引用已丢失——请重新打开启动器。",
      restart: "开始新的录制"
    }
  },
  editor: {
    readOnly: {
      viewer: "只读——请让具有 EDITOR 角色的人员进行修改。",
      runner: "您可以运行测试，但无法编辑此文件。",
      contactAdmin: "联系您的 QA 负责人"
    },
    common: {
      save: "保存",
      cancel: "取消",
      delete: "删除",
      close: "关闭",
      back: "返回",
      add: "添加",
      edit: "编辑",
      create: "创建",
      error: "错误",
      loading: "加载中…",
      noData: "暂无数据。",
      confirm: "确认",
      yes: "是",
      no: "否",
      search: "搜索",
      install: "安装",
      installed: "已安装",
      upgrade: "升级",
      remove: "移除",
      clone: "克隆",
      retry: "重试",
      dismiss: "忽略",
      start: "开始",
      id: "ID",
      status: "状态",
      duration: "时长",
      created: "创建时间",
      actions: "操作",
      name: "名称",
      date: "日期",
      type: "类型",
      prevPage: "← 上一页",
      nextPage: "下一页 →",
      pageOf: "第 {current} 页，共 {total} 页",
      logout: "退出登录",
      menu: "菜单"
    }
  }
}, f_ = {
  robotEditor: {
    visualTab: "Visual Editor",
    flowTab: "Flow",
    codeTab: "Code",
    heal: {
      toggleOn: "Self-Healing: On",
      toggleOff: "Self-Healing: Off",
      toggleOnHint: "Click to revert every Heal* keyword in this file to its bare Browser-library form and remove the RoboScopeHeal import.",
      toggleOffHint: "Click to promote every Browser keyword (Click, Fill Text, …) to its Heal variant and add the RoboScopeHeal library import.",
      toastEnabled: "{count} keywords switched to Self-Healing.",
      toastDisabled: "{count} Heal keywords reverted to their bare form."
    },
    libraryNotInEnvTitle: 'Library "{library}" not installed in environment',
    libraryNotInEnvBody: "The Library import was added to the file, but {library} is not installed in this repo's environment. Only example keywords are shown until you install the package — open the Environments view to add it.",
    libraryNoEnvAtAllBody: "The Library import was added to {library}, but this repo has no environment configured. Configure one in the Environments view to install libraries.",
    libraryInstallDialogTitle: 'Install "{library}"?',
    libraryInstallDialogIntro: 'Library "{library}" is imported but not installed in environment "{env}". Pip-install it now so its full keyword list appears in the palette?',
    libraryInstallDialogPipName: "Pip package name",
    libraryInstallDialogPipHint: "You can edit this if the auto-detected name is wrong.",
    libraryInstallDialogConfirm: "Install",
    libraryInstallDialogInstalling: "Installing…",
    libraryInstallStartedTitle: "Install started",
    libraryInstallStartedBody: "{package} is installing. The palette will refresh once it completes.",
    libraryInstallFailed: "Install failed.",
    libraryAlreadyProvidedTitle: 'Library "{library}" already available',
    libraryAlreadyProvidedBody: '{library} is provided by "{provider}", which is already installed in the environment. The keyword cache may be stale — try restarting the rf-mcp server (Settings → AI → RF MCP) or wait a moment and switch back to this file to refresh.',
    tests: "tests",
    keywordsCount: "keywords",
    initFileWarning: "A suite init file (__init__.robot) cannot contain a Test Cases section.",
    parseError: "Parse Error",
    expandAll: "Expand All",
    collapseAll: "Collapse All",
    settingsSection: "Settings",
    variablesSection: "Variables",
    testCasesSection: "Test Cases",
    keywordsSection: "Keywords",
    addSetting: "Add Setting",
    addVariable: "Add Variable",
    addTestCase: "Add Test Case",
    addKeyword: "Add Keyword",
    addStep: "Add Step",
    addTag: "Add tag...",
    noSettings: 'No settings defined. Click "Add Setting" to add one.',
    noVariables: 'No variables defined. Click "Add Variable" to add one.',
    noTestCases: 'No test cases yet. Click "Add Test Case" to create one.',
    noKeywords: 'No keywords defined. Click "Add Keyword" to create one.',
    unnamedTestCase: "Unnamed Test Case",
    unnamedKeyword: "Unnamed Keyword",
    steps: "steps",
    testCaseName: "Test Case Name",
    testCaseNamePlaceholder: "My Test Case",
    keywordName: "Keyword Name",
    keywordNamePlaceholder: "My Keyword",
    documentation: "Documentation",
    documentationPlaceholder: "Optional documentation...",
    tags: "Tags",
    setup: "Setup",
    setupPlaceholder: "Setup keyword (optional)",
    teardown: "Teardown",
    teardownPlaceholder: "Teardown keyword (optional)",
    timeout: "Timeout",
    timeoutPlaceholder: "e.g. 30s",
    template: "Template",
    templatePlaceholder: "Template keyword (optional)",
    arguments: "Arguments",
    argumentsPlaceholder: "${arg1}    ${arg2}",
    addArgument: "Add argument, e.g. ${name}",
    argTypes: {
      required: "Required",
      optional: "Optional",
      varargs: "Variable",
      kwargs: "Key-Value",
      namedOnly: "Named-only",
      namedOnlyBelow: "Named-only arguments below",
      namePlaceholder: "name",
      defaultPlaceholder: "default",
      addDefault: "Add default value"
    },
    returnValue: "Return Value",
    returnValuePlaceholder: "${result}",
    stepPlaceholder: "Log    Hello World",
    settingValuePlaceholder: "Value",
    argPlaceholder: "arg",
    variableNamePlaceholder: "${VARIABLE_NAME}",
    variableValuePlaceholder: "value",
    keywordPlaceholder: "Keyword Name",
    conditionPlaceholder: "${x} > 5",
    valuePlaceholder: "value",
    exceptPatternPlaceholder: "Error pattern",
    addArg: "Add argument",
    addValue: "Add value",
    settingType: {
      Documentation: "Documentation",
      Library: "Library",
      Resource: "Resource",
      Variables: "Variables",
      SuiteSetup: "Suite Setup",
      SuiteTeardown: "Suite Teardown",
      TestSetup: "Test Setup",
      TestTeardown: "Test Teardown",
      TestTemplate: "Test Template",
      TestTimeout: "Test Timeout",
      ForceTags: "Force Tags",
      DefaultTags: "Default Tags",
      Metadata: "Metadata"
    },
    stepType: {
      keyword: "Keyword",
      assignment: "Assign",
      var: "VAR",
      comment: "Comment",
      for: "FOR",
      if: "IF",
      else_if: "ELSE IF",
      else: "ELSE",
      while: "WHILE",
      try: "TRY",
      except: "EXCEPT",
      finally: "FINALLY",
      end: "END",
      break: "BREAK",
      continue: "CONTINUE",
      return: "RETURN"
    },
    builtinKeyword: "Built-in",
    localKeyword: "Local",
    noSuggestions: "No matching keywords"
  },
  flowEditor: {
    noTestCasesYet: "No test cases in this file yet.",
    noKeywordsYet: "No keywords in this file yet.",
    addTestCase: "Test Case",
    addTestCaseTitle: "Add a new empty test case",
    addKeyword: "Keyword",
    addKeywordTitle: "Add a new empty keyword (user-defined keyword)",
    libraries: "Libraries",
    librariesTitle: "Manage Library and Resource imports for this file",
    librariesNone: "No Library or Resource imports yet.",
    libraryAdd: "Add",
    libraryInputPlaceholder: "Library name (e.g. Browser) or resource/path.resource",
    libraryRemoveTitle: "Remove {name}",
    variables: "Variables",
    variablesTitle: "Define suite variables (*** Variables ***) for this file",
    variablesNone: "No variables defined yet.",
    variableNamePlaceholder: "${NAME}",
    variableValuePlaceholder: "value",
    variableAdd: "Add",
    variableRemoveTitle: "Remove {name}",
    envVarsUsed: "Environment variables used:",
    envVarDefined: "Defined in {env}",
    envVarDefaultUsed: "Not in {env}. Inline default used",
    envVarMissing: "Not defined in environment {env}. The run fails unless the server's own environment provides it.",
    envVarCheckedAgainst: "Checked against environment {env}",
    envVarNotDefined: "(not defined)",
    templateAddRow: "Add row",
    templateAddColumn: "Add column",
    templateRemoveRow: "Remove row",
    suiteSettings: "Settings",
    suiteSettingsTitle: "Suite settings (Setup/Teardown, Tags, Documentation, Metadata)",
    suiteSettingsNone: "No suite settings yet.",
    suiteSettingRemoveTitle: "Remove {name}",
    keywordNotImportedHint: 'Library "{library}" is not imported in this file. Picking this keyword will add the import automatically.',
    keywordNotImportedBadgeTitle: "Library will be imported automatically when this keyword is added.",
    examplesCategoryBadge: "(examples)",
    examplesCategoryHint: "Curated subset shown because the dynamic library introspection returned no data — typically the repo has no environment configured, or the library is not installed in the environment. Configure an environment to see the full keyword list.",
    currentFileCategoryBadge: "this file",
    currentFileCategoryHint: "Keywords defined in the file you are currently editing.",
    resourcesSectionLabel: "Your resources",
    resourcesSectionHint: "Keywords from .robot / .resource files in this repository. Inserting one auto-adds the matching Resource import.",
    resourcePanelLabel: "Resources",
    librariesPanelLabel: "Libraries",
    importAdded: {
      resourceTitle: "Resource imported",
      libraryTitle: "Library imported",
      message: "Added {name} to *** Settings *** so the keyword resolves at runtime."
    },
    sort: {
      title: "Sort keywords",
      mostUsed: "Most used",
      alpha: "A–Z",
      importedFirst: "Imported first"
    },
    filter: {
      title: "Filter what's shown",
      resources: "Your resources",
      importedLibs: "Imported libraries",
      exampleLibs: "Example libraries (not installed)",
      builtin: "BuiltIn",
      hidingCategories: "{count} hidden",
      clear: "Show all"
    },
    resizePanelHint: "Drag to resize the detail panel",
    startNodeLabel: "Start",
    endNodeLabel: "End",
    keyword: "Keyword",
    arguments: "Arguments",
    condition: "Condition",
    returnVars: "Return Variables",
    returnValues: "Return Values",
    returnValuePlaceholder: '${result}, ${count}, "literal"…',
    returnNoValue: "(no value)",
    docMeta: {
      label: "Documentation",
      placeholder: "Describe what this test case (or keyword) does — visible in the HTML report.",
      hint: "Multi-line text is preserved as `...` continuation rows in the saved .robot file.",
      addTitle: "Add a [Documentation] side note to this test case / keyword",
      removeTitle: "Remove the documentation",
      alreadyPresent: "A [Documentation] is already attached — click the side note to edit it."
    },
    debug: {
      btnLabel: "Run up to here",
      btnTitle: "Run this test until the selected step and pause in debug mode",
      unresolvedLine: "Couldn't resolve this step's source line — save the file and try again.",
      startFailed: "Could not start the debug session.",
      unsavedHintTitle: "Save before debugging",
      unsavedHint: "You have unsaved changes. Save the file first so the debug breakpoint matches the source on disk.",
      confirmReplaceTitle: "Replace active debug session?",
      confirmReplace: "A debug session for this file is paused at line {lineFrom}. Disconnect it and start a new one at line {lineTo}?",
      confirmReplaceConfirm: "Disconnect and start"
    },
    heal: {
      toggleLabel: "Self-Healing",
      toggleHint: "Replace this Browser keyword with its Heal variant (locator auto-recovers if the page layout drifts)."
    },
    settingMeta: {
      allPresent: "Every [...] setting is attached — click a side note to edit it.",
      emptyHint: "click to edit",
      documentation: {
        label: "Documentation",
        placeholder: "Describe what this test case (or keyword) does — visible in the HTML report.",
        hint: "Multi-line text is preserved as `...` continuation rows in the saved .robot file.",
        addTitle: "Add a [Documentation] side note",
        removeTitle: "Remove the documentation"
      },
      tags: {
        label: "Tags",
        placeholder: "smoke, regression, slow",
        hint: "Comma-separated. Tags can be selected/excluded at run time via --include / --exclude.",
        addTitle: "Add [Tags] to this test case / keyword",
        removeTitle: "Remove the tags"
      },
      arguments: {
        label: "Arguments",
        placeholder: "${name}, ${greeting}=Hello",
        hint: "Comma-separated. Use ${name}=default for optional arguments, @{name} for varargs.",
        addTitle: "Add [Arguments] to this keyword",
        removeTitle: "Remove the arguments"
      },
      setup: {
        label: "Setup",
        placeholder: "Setup keyword name",
        hint: "Robot keyword executed before this test case / keyword body. Overrides Test Setup.",
        addTitle: "Add a [Setup] hook",
        removeTitle: "Remove the setup"
      },
      teardown: {
        label: "Teardown",
        placeholder: "Teardown keyword name",
        hint: "Robot keyword executed after the body, even on failure. Overrides Test Teardown.",
        addTitle: "Add a [Teardown] hook",
        removeTitle: "Remove the teardown"
      },
      template: {
        label: "Template",
        placeholder: "Template keyword name",
        hint: "Each row in the test body becomes a call to this keyword with the row as arguments.",
        addTitle: "Add a [Template] (data-driven test)",
        removeTitle: "Remove the template"
      },
      timeout: {
        label: "Timeout",
        placeholder: "30s, 5 minutes",
        hint: "Maximum runtime before this test case / keyword is forcefully aborted.",
        addTitle: "Add a [Timeout]",
        removeTitle: "Remove the timeout"
      }
    },
    testCaseSettings: "Test case settings",
    keywordSettings: "Keyword settings",
    comment: "Comment",
    palette: "Keywords",
    searchKeywords: "Search keywords...",
    noResults: "No matching keywords.",
    addArg: "Add argument",
    addVar: "Add variable",
    addValue: "Add value",
    loopVar: "Loop Variable",
    loopFlavor: "Loop Type",
    loopValues: "Values",
    exceptPattern: "Error Pattern",
    exceptVar: "Error Variable",
    varName: "Variable Name",
    varScope: "Scope",
    selector: {
      tooltipHasCandidates: "{count} recorded candidates — click to switch",
      customValueHint: "(custom value, not from recording)",
      candidatesBadge: "× {count}",
      replaceCustomConfirm: `Replace your custom selector

  {current}

with the recorded candidate

  {next}
?`
    },
    argLabels: {
      fallback: "arg {n}",
      extraPositional: "extra positional",
      extraNamed: "extra named",
      defaultPlaceholder: "default: {default}"
    },
    addArgPicker: {
      custom: "Custom value …",
      customLabel: "Custom value",
      customPlaceholder: "value or name=value",
      customAdd: "Add",
      noUnused: "All parameters already added.",
      namedHint: "named"
    },
    docModal: {
      title: "Keyword documentation",
      argsHeader: "Arguments",
      noDoc: "No documentation available.",
      button: "Show documentation",
      loading: "Loading documentation…"
    },
    argTypes: {
      text: "Text",
      integer: "Integer",
      number: "Number",
      yesNo: "On / Off",
      duration: "Duration",
      durationHint: "e.g. 5s, 1min, 00:00:05",
      path: "File path",
      any: "Any",
      choice: "Choice",
      collection: "List / map",
      unknown: "Value",
      optionalSuffix: " (optional)",
      customValue: "custom",
      toggleToText: "Switch to text input (e.g. for a variable)",
      toggleBackToTyped: "Switch back to typed input"
    }
  },
  debug: {
    btn: {
      label: "Debug",
      tooltip: "Re-run this test and pause at the failing line"
    },
    panel: {
      paused: "Paused",
      terminated: "Terminated",
      notPaused: "Waiting for first stop…",
      lineLabel: "Line",
      callStack: "Call stack",
      callStackEmpty: "No call stack yet — execution has not paused.",
      scopes: "Variables",
      scopesEmpty: "No scopes available.",
      scopeEmpty: "No variables in this scope.",
      output: "Output",
      outputEmpty: "No output yet.",
      toolbar: {
        continue: "Continue",
        stepOver: "Step Over",
        stepIn: "Step Into",
        stepOut: "Step Out",
        stop: "Stop"
      }
    },
    error: {
      title: "Debug session error",
      startFailed: "Could not start debug session.",
      commandFailed: "Debug command failed."
    },
    prereq: {
      title: "Debugger not installed",
      body: "{package} is not installed in this project's Python environment. Robot Autotest needs it to run interactive debugging. Install it now?",
      install: "Install",
      cancel: "Cancel",
      installing: "Installing {package}…",
      installFailed: "Install failed. See log for details.",
      installSuccess: "{package} installed successfully."
    }
  },
  recorder: {
    title: "Recording",
    record: "Record",
    startRecording: "Record browser interactions",
    stop: "Stop Recording",
    events: "events",
    generating: "Generating .robot file...",
    newRecording: "New Recording",
    tryAgain: "Try Again",
    cancelled: "Recording cancelled",
    waitingForEvents: "Waiting for browser interactions...",
    generatedFile: "Generated .robot File",
    completed: "Recording Complete",
    completedMsg: "Robot Framework test file has been generated.",
    failed: "Recording Failed",
    failedMsg: "An error occurred during recording.",
    selector: {
      swapAriaLabel: "Swap selector strategy",
      editOrAddAriaLabel: "Edit selector or add a custom one",
      editTitle: "Edit this selector",
      addCustom: "Add a custom selector",
      valuePlaceholder: "css=.btn-primary, //button[…], text=Click me, …",
      strategyLabel: "Strategy",
      verifiedUniqueTitle: "Verified unique on the live page at recording time",
      effectiveLabel: "Effective",
      effectivePlaceholder: "iframe[…] >>> selector >> nth=0",
      effectiveTitle: "What gets written verbatim to the .robot file. Default: auto-composed from strategy + value + iframe chain. A custom value here overrides the composition.",
      effectiveResetTitle: "Reset to auto-composed value",
      effectiveOverrideTitle: "Override active — replaces iframe wrap, xpath= / text= prefix and nth=0",
      effectiveOverrideBadge: "override active",
      strategy: {
        testid: "Test ID",
        aria: "ARIA",
        text: "Text",
        css: "CSS",
        xpath: "XPath",
        pw_locator: "Playwright",
        automation_id: "AutomationId",
        uia_name: "UIA Name",
        uia_class_name: "UIA Class"
      }
    },
    launcher: {
      heading: "Start a recording",
      hint: "Record a user flow and turn it into a Robot Framework test. The browser opens in a controlled window — no extension install required.",
      repoLabel: "Save to repository",
      targetUrlLabel: "Open URL (optional)",
      targetUrlPlaceholder: "https://example.com",
      targetUrlHint: "Leave blank to start at about:blank — you can navigate manually once the browser opens.",
      targetUrlInvalid: "URL must start with http:// or https://",
      transportLabel: "What do you want to record?",
      transport: {
        web: "Web — browser-based flow",
        windows: "Desktop (Windows)",
        macos: "Desktop (macOS)"
      },
      comingSoon: "coming soon",
      record: "Start recording",
      starting: "Starting…",
      startFailed: "Could not start the recording. Please try again.",
      remote: {
        heading: "Browser recording not available on this server",
        body: "The Robot Autotest backend has no viewable browser (typical for a remote server deployment), so it cannot open a Chromium window for you to record in. Install the browser recording extension on your own machine instead — it records in your browser and streams the events to this backend.",
        override: "Admins can force-enable browser recording through a backend environment variable."
      },
      reset: {
        hint: "Recording stuck or browser window won't open? Click Reset to clean up any leftover sessions and orphan browser processes.",
        label: "Reset stuck recordings",
        busy: "Resetting…",
        noneFound: "No stuck recordings found.",
        done: "Reset done",
        doneDetail: "Cleaned up {count} stuck recording session(s).",
        failed: "Reset failed. Please try again."
      }
    },
    live: {
      heading: "Recording",
      status: {
        connecting: "Connecting",
        live: "Live",
        done: "Done",
        error: "Error"
      },
      lifecycle: {
        connecting: "Connecting…",
        browser_starting: "Launching Chromium…",
        browser_ready: "Browser ready",
        browser_restarting: "Restarting browser…",
        browser_crashed: "Browser crashed",
        done: "Done",
        error: "Error"
      },
      uptimeTitle: "Time since the browser became ready",
      effectiveTitle: "The exact text that lands as the keyword's first argument in the .robot file.",
      restartBrowser: "Restart browser",
      restartingBrowser: "Restarting…",
      restartBrowserHint: "Kills the current Chromium and spawns a fresh one. Captured commands are preserved; you may need to re-navigate to your test page.",
      restartFailed: "Could not restart the browser.",
      crashTitle: "Browser crashed:",
      waiting: "Waiting for your first interaction…",
      pathLabel: "Save as",
      pathPlaceholder: "flows/recording",
      save: "Save",
      stopAndSave: "Stop and save",
      deleteStep: "Delete this step",
      streamError: "Lost connection to the recorder.",
      startFailed: "Could not launch the browser.",
      saveFailed: "Could not save the recording.",
      repoMissing: "Repository reference lost — open the launcher again.",
      restart: "Start a new recording"
    }
  },
  editor: {
    readOnly: {
      viewer: "Read-only — ask an EDITOR to change this.",
      runner: "You can run tests but not edit this file.",
      contactAdmin: "Contact your QA lead"
    },
    common: {
      save: "Save",
      cancel: "Cancel",
      delete: "Delete",
      close: "Close",
      back: "Back",
      add: "Add",
      edit: "Edit",
      create: "Create",
      error: "Error",
      loading: "Loading...",
      noData: "No data available.",
      confirm: "Confirm",
      yes: "Yes",
      no: "No",
      search: "Search",
      install: "Install",
      installed: "Installed",
      upgrade: "Upgrade",
      remove: "Remove",
      clone: "Clone",
      retry: "Retry",
      dismiss: "Dismiss",
      start: "Start",
      id: "ID",
      status: "Status",
      duration: "Duration",
      created: "Created",
      actions: "Actions",
      name: "Name",
      date: "Date",
      type: "Type",
      prevPage: "← Back",
      nextPage: "Next →",
      pageOf: "Page {current} of {total}",
      logout: "Logout",
      menu: "Menu"
    }
  }
}, R_ = {
  "zh-CN": p_,
  "en-US": f_
};
export {
  If as FlowEditor,
  ea as LOOP_FLAVORS,
  zf as ReadOnlyBanner,
  N_ as RobotEditor,
  Ta as STEP_TYPES,
  Ea as VAR_SCOPES,
  hm as collectRbsIdsFromContent,
  V_ as configureEditorAdapter,
  ye as editorAdapter,
  R_ as editorMessages,
  Da as editorScope,
  La as emitMultilineSetting,
  lm as escapeRfCell,
  as as escapeRfToken,
  mm as initFileHasTestCases,
  fm as isInitFile,
  Bo as makeStep,
  gm as parseRobotText,
  dm as parseStepLine,
  A_ as resetEditorAdapter,
  L_ as resetEditorScope,
  vm as serializeRobotForm,
  pm as serializeStep,
  S_ as setEditorScope,
  Sa as unescapeRfCell,
  Cn as unescapeRfToken
};
//# sourceMappingURL=editor.js.map
