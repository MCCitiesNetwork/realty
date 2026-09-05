import { Button, Result } from "antd";
import { Link } from "react-router-dom";
import { Page } from "../ui/Page";

export function NotFoundScreen() {
  return (
    <Page>
      <Result
        status="404"
        title="Nothing at this address"
        subTitle="The page you followed does not exist."
        extra={<Link to="/"><Button type="primary">Back to Realty</Button></Link>}
      />
    </Page>
  );
}
