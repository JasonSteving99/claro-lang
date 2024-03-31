import hljs from 'highlight.js';
import React from 'react';
import ReactDOM from 'react-dom/client';

export class HighlightJS extends React.Component {
    constructor(props) {
      super(props)
      this.setEl = this.setEl.bind(this)
    }
    componentDidMount() {
        this.highlightCode();
    }

    componentDidUpdate() {
        this.highlightCode();
    }

    highlightCode() {
        const nodes = this.el.querySelectorAll('pre code');

        for (let i = 0; i < nodes.length; i++) {
            let dataHighlighted = nodes[i].attributes.getNamedItem("data-highlighted");
            if (dataHighlighted) {
              // Make sure that if this element has already been highlighted before, we signal to HighlightJS that we
              // explicitly want to highlight it all over again. This is only getting re-rendered b/c the user actually
              // clicked on a new module api.
              nodes[i].attributes.removeNamedItem("data-highlighted");
            }
            hljs.highlightElement(nodes[i]);

            this.walkHighlightedCode(nodes[i]);
        }
    }

    walkHighlightedCode(codeEl) {
      for (let i = 0; i < codeEl.childNodes.length; i++) {
        const el = codeEl.childNodes[i];
        if (this.isExpectedSpan(el, 'newtype')) {
          const typeNameEl = codeEl.childNodes[++i];
          const typeNameStr = typeNameEl.nodeValue.trim();
          const typedefEl = this.wrapInElement("span", [typeNameEl], {"claro-type-id": typeNameStr});
          if (this.props.targetType && this.props.targetType === typeNameStr) {
            typedefEl.setAttribute('class', 'blink');
          }
        } else if (i + 2 < codeEl.childNodes.length) {
          // We're looking for something like `Dep::Foo` so that we can link to the module API.
          const nextNode = codeEl.childNodes[i + 1];
          if (this.isExpectedSpan(nextNode, '::')) {
            // Make sure that `this` doesn't escape the current scope.
            const setSelectedModule = this.props.setSelectedModule;
            const setTargetType = this.props.setTargetType;

            const depName = el.textContent.trim();
            const targetType = codeEl.childNodes[i + 2].textContent.trim();

            // Validate whether this reference is actually something that we can link to.
            if(!(depName in this.props.selectedModuleDepsTooltips)) {
              console.error(`Referenced Module Not Found: ${depName}::${targetType}`)
              i++;
              continue;
            }

            const _selectedModule = this.props.selectedModuleDepsTooltips[depName].path;
            function _setSelectedModule(e) {
              setSelectedModule(_selectedModule);
              setTargetType(targetType);
            }
            // First replace the dep reference with its tooltip
            const newTargetTypeNode = document.createElement('span');
            const newTargetTypeNodeRoot = ReactDOM.createRoot(newTargetTypeNode);
            newTargetTypeNodeRoot.render(
              <>
                <>{codeEl.childNodes[i].nodeValue.match(/^\s*/)[0]}</>
                {this.props.selectedModuleDepsTooltips[depName].tooltip}
                <span className="hljs-keyword2">::</span>
                <u className='clickable' onClick={_setSelectedModule}>
                  {targetType}
                </u>
                <>{codeEl.childNodes[i + 2].nodeValue.match(/\s*$/)[0]}</>
              </>
            );
            // Just wrap up all those nodes to make it easy to replace them all at once.
            const matchedDepTypeRef = this.wrapInElement("span", Array.from(codeEl.childNodes).slice(i, i + 3));
            codeEl.replaceChild(newTargetTypeNode, matchedDepTypeRef);

            i++;
          }
        }
      }
    }

    isExpectedSpan(node, wrapped) {
      return node.nodeName && node.nodeName.toLowerCase() === 'span' && node.childNodes[0].nodeValue === wrapped;
    }

    wrapInElement(tag, children, attributes = {}, eventListener = null) {
      const newEl = document.createElement(tag);
      children[0].parentElement.insertBefore(newEl, children[0]);
      children.forEach(c => newEl.appendChild(c));
      Object.entries(attributes).forEach(p => newEl.setAttribute(p[0], p[1]));
      if (eventListener) {
        newEl.addEventListener("click", eventListener);
      }
      return newEl;
    }

    setEl(el) {
        this.el = el;
    };

    render() {
        const {children, className, element: Element, innerHTML, style, id} = this.props;
        const props = { ref: this.setEl, className};

        if (innerHTML) {
            props.dangerouslySetInnerHTML = { __html: children };
            if (Element) {
                return <Element {...props} />;
            }
            return <div {...props} />;
        }

        if (Element) {
            return <Element {...props}>{children}</Element>;
        }
        return <pre ref={this.setEl} style={style} id={id}><code className={className}>{children}</code></pre>;
    }
}

Highlight.defaultProps = {
    innerHTML: false,
    className: null,
    element: null,
};

// Customizing a Claro highlight.js impl.
hljs.registerLanguage(
  "claro",
  function(e) {
    return {
      keywords: {
        keyword1: "HttpService alias atom blocking break consumer continue else flag for function graph if immutable lazy match newtype node opaque provider repeat return root static var where while ",
        keyword2: "and as cast contract copy endpoint_handlers fromJson getHttpClient implement in initializers instanceof mut not or requires sleep unwrap unwrappers ",
        keyword3: "Error HttpClient ParsedJson _ boolean char double float future int lambda long oneof string struct tuple ",
        keyword4: "case using",
        literal: "true false"
      },
      contains: [
        {
          scope: 'string',
          begin: '"', end: '"',
          className: "string",
          contains: [e.BACKSLASH_ESCAPE],
          variants: [e.APOS_STRING_MODE, e.QUOTE_STRING_MODE]
        },
        {
          className: 'number',
          begin: /\d+[LF]?/
        },
        {
          className: 'keyword2',
          begin: /::/
        },
        hljs.COMMENT(
          "#", // begin
          "$" // end
        ),
      ]
      /* Add highlighting for the first group of tokens. */
      .concat("!= \\* \\+ \\+\\+ - -- -> / : ; < <- <= = == > >= \\?= @ , { } \\[ ] \\( \\) \\^ \\|>"
               .split(" ")
               .map((x) => { return {className: "keyword2", begin: x}; }))
      /* Add highlighting for the second group of tokens. */
      .concat([{className: "keyword3", begin: /\s_\s/}])
      /* Add highlighting for the third group of tokens. */
      .concat("\\? \\|"
               .split(" ")
               .map((x) => { return {className: "keyword4", begin: x}; }))
    };
  }
);