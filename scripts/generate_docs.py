import jinja2
from pathlib import Path
import os
import re
import sys
import yaml

from argparse import ArgumentParser
from tabulate import tabulate

METHOD_DEF_REGEX = re.compile(
    r'^(public|def) (?P<method_name>.+?)\((?P<method_args>.+?)\) \{$')
METHOD_END_REGEX = re.compile(r'^\}$')
SCRIPT_PATH = Path()


class Arg:
    def __init__(self, a_type, name, default=None):
        self.type = a_type
        self.name = name
        self.default = default

    def __repr__(self) -> str:
        return f"Arg({self.type}: {self.name}|{self.default})"

    def __str__(self) -> str:
        return f"{self.type} {self.name}"

    @staticmethod
    def parse(data):
        args = []
        for arg in [x.strip() for x in data.split(',')]:
            arg_def = [a for a in arg.split(' ')]
            if len(arg_def) > 1:
                arg_type = arg_def[0]
                n = str(arg_def[1]).split('=')
                if len(n) > 1:
                    arg_name = n[0]
                    arg_default = n[1]
                else:
                    arg_name = arg_def[1]
                    arg_default = None
            else:
                arg_type = 'Object'
                n = str(arg_def).split('=')
                if len(n) > 1:
                    arg_name = n[0]
                    arg_default = n[1]
                else:
                    arg_name = arg_def[0]
                    arg_default = None

            args.append(Arg(arg_type, arg_name, arg_default))
        return args


class Method:
    def __init__(self, method_name, method_body, method_args, doc):
        self.name = method_name
        self.args = Arg.parse(method_args)
        self.body = method_body
        self.doc = doc

    def __repr__(self) -> str:
        return f"Method({self.name}, {self.args})"

    def __str__(self) -> str:
        return f"{self.name}({', '.join([x.type for x in self.args])})"


def parse_file(file_lines):
    functions = []

    function_groups = {}

    function_start_line = 0
    function_end_line = 0

    for i, f in enumerate(file_lines):
        if f == '\n':
            continue
        line_is_method_def = METHOD_DEF_REGEX.match(f)
        if function_start_line != 0:
            if METHOD_END_REGEX.match(f):
                function_end_line = i
                function_doc_lines = []
                if file_lines[function_start_line - 1].strip() == '*/':
                    doc_end_line = function_start_line - 1
                    for doc_i, line in enumerate(file_lines[doc_end_line::-1]):
                        if line.strip() == '/*':
                            doc_start_line = doc_end_line - doc_i
                            function_doc_lines = file_lines[doc_start_line + 1:
                                                            doc_end_line]
                            break
                functions.append(
                    Method(
                        method_name=function_groups.get('method_name'),
                        method_args=function_groups.get('method_args'),
                        method_body=file_lines[function_start_line + 1:
                                               function_end_line - 1],
                        doc='\n'.join(
                            [x for x in function_doc_lines if x != '\n'])))
                function_start_line = 0
        else:
            if line_is_method_def:
                function_start_line = i
                function_groups = line_is_method_def.groupdict()

    return functions


def parse_yaml(text):
    return yaml.load(text)


def create_markdown_table(values):
    rows = []
    columns = ['Type', 'Name', 'Default']

    for row in values:
        d = [row.type, row.name]
        if row.default:
            d.append(row.default)
        else:
            d.append('')
        rows.append(d)

    return tabulate(rows, [x.title() for x in columns], tablefmt="pipe")


def render_jinja_template(tpl_path, context):
    path, filename = os.path.split((SCRIPT_PATH / tpl_path).resolve())
    print(f"Searching for templates in {path}")
    env = jinja2.Environment(loader=jinja2.FileSystemLoader(path))
    print(f"Available templates {env.list_templates('j2')}")
    return env.get_template(filename).render(context)


def create_index_markdown(groovy_files, docs_folder):
    print('Generating index.md ...')
    links = []
    for groovy_file in sorted(groovy_files):
        workflow_name = os.path.splitext(groovy_file)[0]
        links.append(
            f"* [{workflow_name.title()}]({workflow_name.upper()}.md)")

    rendered_template = render_jinja_template(
        '.github/PAGES_INDEX.md.j2', {
            'WORKFLOW_LINKS': '\n'.join(links)
        })

    with open(docs_folder / 'index.md', 'w') as w:
        w.write(rendered_template + '\n')


def create_markdown_doc(name, docs_folder, functions):
    docs_folder.mkdir(exist_ok=True)
    lines = [f"# com.concur.{name.replace('.groovy', '')}"]
    for g_function in functions:
        if g_function.name == 'getStageName':
            continue
        lines.append(f"\n## {str(g_function)}")
        if g_function.doc:
            function_yaml_def = parse_yaml(g_function.doc)
            lines.append(f"\n> {function_yaml_def.get('description')}")
            if function_yaml_def.get('note'):
                lines.append(f"\n__**{function_yaml_def.get('note')}**__")
            lines.append(f"\n{create_markdown_table(g_function.args)}")
            examples = function_yaml_def.get('examples')
            if examples:
                for i, example in enumerate(examples):
                    lines.append(f"\n### Example {i+1}")
                    lines.append(f"\n```groovy\n{example}\n```")
            example = function_yaml_def.get('example')
            if example:
                lines.append(f"\n### Example")
                lines.append(
                    f"\n```groovy\n{function_yaml_def.get('example')}\n```")

    with (docs_folder / name.upper().replace('.GROOVY',
                                             '.md')).open(mode='w') as w:
        w.write('\n'.join(lines))


def entry_point():
    parser = ArgumentParser()
    parser.add_argument('-o', '--out-path')

    args = parser.parse_args()

    if args.out_path is None:
        parser.print_usage()
        exit(1)
    groovy_files = (SCRIPT_PATH / 'src' / 'com' / 'concur').rglob('*.groovy')
    for fil in groovy_files:
        with fil.open() as groovy_file:
            lines = groovy_file.read().splitlines()
            functions = parse_file(lines)
            if not functions:
                continue
            print(f"Generating documentation for {fil.name}...")
            create_markdown_doc(
                name=fil.name,
                docs_folder=SCRIPT_PATH / args.out_path,
                functions=functions)
    # create_index_markdown([x.name for x in SCRIPT_PATH.rglob('*.groovy') if x.name != 'example.groovy'], SCRIPT_PATH / args.out_path)


if __name__ == '__main__':
    entry_point()
